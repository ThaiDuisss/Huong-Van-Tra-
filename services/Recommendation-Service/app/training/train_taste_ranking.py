"""Train and evaluate one inactive reranker from a pinned, verified evidence export."""
import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import tempfile

import numpy as np
from pydantic import ValidationError

from app.recommendation.taste_ranking_artifact import ActivationManifest, LogisticModel, TasteRankingArtifact
from app.recommendation.taste_ranking_features import (
    FEATURE_COLUMNS, MODES, RANKING_FEATURE_SETS, FeatureError, Mode, RankingFeatureSet,
    feature_names, taste_column_indices, vector,
)
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import V1ModelError, fingerprint
from app.repositories.explore_history_repository import ExploreHistoryError
from app.schemas.taste_catalog import SnapshotError
from app.training.taste_ranking_dataset import (
    OUTCOME_LABEL_POLICY, Dataset, DatasetBlocked, EvidenceBundle, RankingQuery, build_dataset,
)
from app.training.level2_evidence_export import read_checked, verify_source_packets
from app.training.logistic_fit import fit_logistic_parameters
from app.training.taste_ranking_evaluation import evaluate, learned_top5
from offline_benchmark.metrics.ranking_metrics import ranking_metrics

REGULARIZATION_GRID = (0.001, 0.01, 0.1)
SIMULATED_REGISTRY_DIRECTORY = "isolated-simulation-registry"


@dataclass(frozen=True, slots=True)
class LogisticSelection:
    model: LogisticModel
    training_rows: int
    training_positive_rows: int
    training_customers: int
    validation_grid: tuple[dict, ...]


def _fit(matrix: np.ndarray, labels: np.ndarray, penalty: float, attributes: tuple[str, ...],
         categories: tuple[str, ...]) -> tuple[LogisticModel, dict]:
    centers, scales, result = fit_logistic_parameters(matrix, labels, penalty)
    if not result.success or not np.isfinite(result.x).all():
        raise DatasetBlocked("LOGISTIC_FIT_DID_NOT_CONVERGE")
    model = LogisticModel(
        attributes=list(attributes), categories=list(categories), feature_names=list(feature_names(attributes, categories)),
        centers=centers.tolist(), scales=scales.tolist(), coefficients=result.x[:-1].tolist(),
        intercept=float(result.x[-1]), regularization=penalty,
    )
    return model, {
        "converged": bool(result.success), "optimizer_status": int(result.status),
        "iterations": int(result.nit), "function_evaluations": int(result.nfev),
        "penalized_objective": float(result.fun), "gradient_infinity_norm": float(np.linalg.norm(result.jac, np.inf)),
    }


def select_logistic(
    mode: Mode, train: tuple[RankingQuery, ...], validation: tuple[RankingQuery, ...],
    attributes: tuple[str, ...], feature_set: RankingFeatureSet = "BEHAVIORAL_PLUS_TASTE",
) -> LogisticSelection:
    """Fit the fixed grid from TRAIN rows and rank it on the common VALIDATION cohort."""
    if (any(q.mode != mode or q.split != "TRAIN" for q in train)
            or any(q.mode != mode or q.split != "VALIDATION" for q in validation)
            or feature_set not in RANKING_FEATURE_SETS):
        raise DatasetBlocked("INVALID_TRAIN_OR_VALIDATION_SELECTION_INPUT")
    informative_train = tuple(query for query in train if query.informative)
    informative_validation = tuple(query for query in validation if query.informative)
    if len({query.customer_id for query in informative_train}) < 2 or not informative_validation:
        raise DatasetBlocked(f"INSUFFICIENT_{mode}_TRAIN_OR_VALIDATION_SIGNAL")
    rows = [row for query in train if query.can_rerank for row in query.samples if row.features.has_signal]
    categories = tuple(sorted({row.features.category for row in rows if row.features.category
                               and row.features.category.strip()}))
    matrix = np.asarray([vector(row.features, attributes, categories) for row in rows], dtype=float)
    labels = np.asarray([row.label for row in rows], dtype=float)
    # Require variation in observed compatibility values, beyond mere survey/profile presence.
    first_taste_column = len(FEATURE_COLUMNS) + len(categories)
    values, missing = matrix[:, first_taste_column::2], matrix[:, first_taste_column + 1::2]
    if not any(len(set(values[missing[:, index] == 0, index])) > 1 for index in range(values.shape[1])):
        raise DatasetBlocked(f"INSUFFICIENT_{mode}_TASTE_VARIATION")
    if feature_set == "BEHAVIORAL_ONLY":
        matrix[:, taste_column_indices(feature_names(attributes, categories))] = 0

    def validation_score(model: LogisticModel) -> float:
        scores = []
        for query in informative_validation:
            top5, applied = learned_top5(query, model)
            if not applied:
                raise DatasetBlocked("VALIDATION_RERANKING_FAILED")
            relevant = tuple(row.features.product_id for row in query.samples if row.label)
            scores.append(ranking_metrics(top5, relevant).ndcg_at_5)
        return sum(scores) / len(scores)

    selection = []
    for penalty in REGULARIZATION_GRID:
        model, convergence = _fit(matrix, labels, penalty, attributes, categories)
        selection.append((model, {"regularization": penalty, "ndcg_at_5": validation_score(model), **convergence}))
    model, _ = max(selection, key=lambda item: (item[1]["ndcg_at_5"], item[0].regularization))
    return LogisticSelection(model, len(rows), int(labels.sum()), len({q.customer_id for q in train if q.can_rerank}),
                             tuple(details for _, details in selection))


def train_and_evaluate(
    dataset: Dataset, mode: Mode, model_version: str, feature_set: RankingFeatureSet = "BEHAVIORAL_PLUS_TASTE",
) -> tuple[TasteRankingArtifact, dict]:
    if dataset.matching_policy_version != EXACT_POLICY_VERSION:
        raise DatasetBlocked("MATCHING_POLICY_INCOMPATIBLE")
    if feature_set == "BEHAVIORAL_ONLY" and dataset.provenance != "SIMULATED_DEMO":
        raise DatasetBlocked("BEHAVIORAL_ABLATION_REQUIRES_SIMULATION")
    train = tuple(query for query in dataset.queries if query.mode == mode and query.split == "TRAIN")
    validation = tuple(query for query in dataset.queries if query.mode == mode and query.split == "VALIDATION")
    test = tuple(query for query in dataset.queries if query.mode == mode and query.split == "TEST")
    selection = select_logistic(mode, train, validation, dataset.attributes, feature_set)
    model = selection.model
    trained_at = datetime.now(timezone.utc)
    if not any(query.informative for query in test):
        raise DatasetBlocked(f"INSUFFICIENT_{mode}_TEST_SIGNAL")
    test_metrics = evaluate(mode, test, model, matching_policy_version=dataset.matching_policy_version)
    if not test_metrics["cohorts"]["WITH_TASTE"]["reranked_queries"]:
        raise DatasetBlocked("TEST_RERANKING_FAILED")
    report = {
        "mode": mode,
        "model_version": model_version,
        "dataset_sha256": dataset.fingerprint,
        "taxonomy_version": dataset.taxonomy_version,
        "taxonomy_sha256": dataset.taxonomy_sha256,
        "matching_policy_version": dataset.matching_policy_version,
        "outcome_label_policy": OUTCOME_LABEL_POLICY,
        "training_provenance": dataset.provenance,
        "training_feature_set": feature_set,
        "training_rows": selection.training_rows,
        "training_customers": selection.training_customers,
        "training_positive_rows": selection.training_positive_rows,
        "validation_selection": list(selection.validation_grid),
        "validation": evaluate(mode, validation, model, matching_policy_version=dataset.matching_policy_version),
        "test": test_metrics,
    }
    artifact = TasteRankingArtifact(
        model_version=model_version, mode=mode, taxonomy_version=dataset.taxonomy_version,
        taxonomy_sha256=dataset.taxonomy_sha256, dataset_sha256=dataset.fingerprint,
        matching_policy_version=dataset.matching_policy_version,
        training_cutoff=max(query.outcome_freeze for query in train),
        selection_cutoff=max(query.outcome_freeze for query in validation),
        evaluation_cutoff=max(query.outcome_freeze for query in test),
        trained_at=trained_at, evaluated_at=datetime.now(timezone.utc), evaluation_status="EVALUATED",
        training_provenance=dataset.provenance, training_feature_set=feature_set,
        evaluation_sha256=fingerprint(report), model=model, model_sha256=fingerprint(model.model_dump(mode="json")),
    )
    return artifact, report


def _write_json(path: Path, document: dict) -> str:
    raw = json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False).encode("utf-8")
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()


def load_training_evidence(
    path: Path, sha256: str, capture_directory: Path | None, *, test_fixture: bool = False,
    simulated_demo: bool = False,
) -> EvidenceBundle:
    if test_fixture and simulated_demo:
        raise DatasetBlocked("EXPLICIT_TRAINING_EVIDENCE_MODE_REQUIRED")
    evidence = EvidenceBundle.model_validate_json(read_checked(path, sha256))
    required = "SIMULATED_DEMO" if simulated_demo else "TEST_FIXTURE" if test_fixture else "VERIFIED_COMMERCIAL"
    if evidence.provenance != required:
        raise DatasetBlocked("TRAINING_EVIDENCE_PROVENANCE_REQUIRED")
    if not test_fixture:
        if not capture_directory:
            code = "SIMULATION_SOURCE_PACKETS_REQUIRED" if simulated_demo else "COMMERCIAL_SOURCE_PACKETS_REQUIRED"
            raise DatasetBlocked(code)
        verify_source_packets(evidence, capture_directory)
    # Simulation timestamps belong to a separate clock. The physical generation time is in its manifest.
    if not simulated_demo and evidence.exported_at > datetime.now(timezone.utc):
        raise DatasetBlocked("EVIDENCE_EXPORT_IN_FUTURE")
    return evidence


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        epilog="Use --schema evidence for the export contract. Pin the reviewed export's SHA-256 and "
               "provide --capture-directory for the audited commercial source packets. "
               "Run separately per objective. Use --schema activation for the operator approval manifest.",
    )
    parser.add_argument("--schema", choices=("evidence", "activation"))
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--evidence-sha256")
    provenance = parser.add_mutually_exclusive_group()
    provenance.add_argument("--test-fixture", action="store_true", help="Accept explicitly labelled fixture evidence only")
    provenance.add_argument("--simulated-demo", action="store_true", help="Use only isolated simulation evidence")
    parser.add_argument("--capture-directory", type=Path, help="Operator-approved commercial source captures")
    parser.add_argument("--mode", choices=MODES)
    parser.add_argument("--feature-set", choices=RANKING_FEATURE_SETS, default="BEHAVIORAL_PLUS_TASTE")
    parser.add_argument("--model-version")
    parser.add_argument("--output", type=Path, help="New output directory outside the repository")
    args = parser.parse_args()
    if args.schema:
        schema = EvidenceBundle if args.schema == "evidence" else ActivationManifest
        print(json.dumps(schema.model_json_schema(), indent=2))
        return 0
    if not all((args.evidence, args.evidence_sha256, args.mode, args.model_version, args.output)):
        parser.error("Training requires --evidence, --evidence-sha256, --mode, --model-version and --output")
    source = Path(__file__).resolve()
    repository = next((parent for parent in source.parents if (parent / ".git").exists()), source.parents[2])
    output = args.output.resolve()
    if output.is_relative_to(repository) or output.exists():
        parser.error("--output must be a new directory outside the repository")
    in_simulated_registry = any(part.casefold() == SIMULATED_REGISTRY_DIRECTORY for part in output.parent.parts)
    if args.simulated_demo != in_simulated_registry:
        parser.error("Only --simulated-demo may write in isolated-simulation-registry, and it must write there")
    if args.feature_set == "BEHAVIORAL_ONLY" and not args.simulated_demo:
        parser.error("--feature-set BEHAVIORAL_ONLY requires --simulated-demo")
    try:
        evidence = load_training_evidence(args.evidence, args.evidence_sha256, args.capture_directory,
                                          test_fixture=args.test_fixture, simulated_demo=args.simulated_demo)
        artifact, report = train_and_evaluate(build_dataset(evidence), args.mode, args.model_version, args.feature_set)
    except DatasetBlocked as failure:
        print(f"DATASET_BLOCKED {failure}", file=sys.stderr)
        return 2
    except (OSError, ValidationError, ExploreHistoryError, FeatureError, SnapshotError, V1ModelError) as failure:
        print(f"DATASET_BLOCKED {type(failure).__name__}", file=sys.stderr)
        return 2
    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=args.output.parent, prefix=".taste-ranker-") as staging:
            directory = Path(staging)
            _write_json(directory / "evaluation.json", report)
            checksum = _write_json(directory / "reranker.json", artifact.model_dump(mode="json"))
            directory.rename(args.output)
    except OSError:
        print("OUTPUT_WRITE_FAILED", file=sys.stderr)
        return 1
    print(json.dumps({"mode": artifact.mode, "activation_status": artifact.activation_status,
                      "evaluation_sha256": artifact.evaluation_sha256, "artifact_sha256": checksum}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
