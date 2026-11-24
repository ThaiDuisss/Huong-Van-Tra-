"""Train and evaluate one inactive reranker from a pinned, verified evidence export."""
import argparse
from datetime import datetime, timezone
import hashlib
import hmac
import json
from pathlib import Path
import sys
import tempfile

import numpy as np
from pydantic import ValidationError
from scipy.optimize import minimize
from scipy.special import expit

from app.recommendation.taste_ranking_artifact import ActivationManifest, LogisticModel, TasteRankingArtifact
from app.recommendation.taste_ranking_features import FEATURE_COLUMNS, MODES, FeatureError, Mode, feature_names, vector
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.recommendation.v1_model import V1ModelError, fingerprint
from app.repositories.explore_history_repository import ExploreHistoryError
from app.schemas.taste_catalog import SnapshotError
from app.training.taste_ranking_dataset import (
    OUTCOME_LABEL_POLICY, Dataset, DatasetBlocked, EvidenceBundle, RankingQuery, build_dataset,
)
from app.training.taste_ranking_evaluation import evaluate, learned_top5
from offline_benchmark.metrics.ranking_metrics import ranking_metrics

REGULARIZATION_GRID = (0.001, 0.01, 0.1)


def _informative(query: RankingQuery) -> bool:
    labels = {row.label for row in query.samples if row.features.has_signal}
    return query.can_rerank and len(labels) == 2


def _fit(matrix: np.ndarray, labels: np.ndarray, penalty: float, attributes: tuple[str, ...],
         categories: tuple[str, ...]) -> LogisticModel:
    centers = matrix.mean(axis=0)
    scales = matrix.std(axis=0)
    scales[scales < 1e-12] = 1
    design = (matrix - centers) / scales

    def objective(parameters):
        weights, intercept = parameters[:-1], parameters[-1]
        logits = design @ weights + intercept
        loss = np.mean(np.logaddexp(0, logits) - labels * logits) + penalty * np.dot(weights, weights) / 2
        residual = expit(logits) - labels
        gradient = np.append(design.T @ residual / len(labels) + penalty * weights, residual.mean())
        return loss, gradient

    result = minimize(objective, np.zeros(matrix.shape[1] + 1), method="L-BFGS-B", jac=True)
    if not result.success or not np.isfinite(result.x).all():
        raise DatasetBlocked("LOGISTIC_FIT_DID_NOT_CONVERGE")
    return LogisticModel(
        attributes=list(attributes), categories=list(categories), feature_names=list(feature_names(attributes, categories)),
        centers=centers.tolist(), scales=scales.tolist(), coefficients=result.x[:-1].tolist(),
        intercept=float(result.x[-1]), regularization=penalty,
    )


def train_and_evaluate(dataset: Dataset, mode: Mode, model_version: str) -> tuple[TasteRankingArtifact, dict]:
    if dataset.matching_policy_version != EXACT_POLICY_VERSION:
        raise DatasetBlocked("MATCHING_POLICY_INCOMPATIBLE")
    train = tuple(query for query in dataset.queries if query.mode == mode and query.split == "TRAIN")
    validation = tuple(query for query in dataset.queries if query.mode == mode and query.split == "VALIDATION")
    test = tuple(query for query in dataset.queries if query.mode == mode and query.split == "TEST")
    informative_train = tuple(query for query in train if _informative(query))
    informative_validation = tuple(query for query in validation if _informative(query))
    if len({query.customer_id for query in informative_train}) < 2 or not informative_validation:
        raise DatasetBlocked(f"INSUFFICIENT_{mode}_TRAIN_OR_VALIDATION_SIGNAL")
    rows = [row for query in train if query.can_rerank for row in query.samples if row.features.has_signal]
    categories = tuple(sorted({row.features.category for row in rows if row.features.category
                               and row.features.category.strip()}))
    matrix = np.asarray([vector(row.features, dataset.attributes, categories) for row in rows], dtype=float)
    labels = np.asarray([row.label for row in rows], dtype=float)
    # Require variation in observed compatibility values, beyond mere survey/profile presence.
    first_taste_column = len(FEATURE_COLUMNS) + len(categories)
    values, missing = matrix[:, first_taste_column::2], matrix[:, first_taste_column + 1::2]
    if not any(len(set(values[missing[:, index] == 0, index])) > 1 for index in range(values.shape[1])):
        raise DatasetBlocked(f"INSUFFICIENT_{mode}_TASTE_VARIATION")

    def validation_score(model: LogisticModel) -> float:
        scores = []
        for query in informative_validation:
            top5, applied = learned_top5(query, model)
            if not applied:
                raise DatasetBlocked("VALIDATION_RERANKING_FAILED")
            relevant = tuple(row.features.product_id for row in query.samples if row.label)
            scores.append(ranking_metrics(top5, relevant).ndcg_at_5)
        return sum(scores) / len(scores)

    models = [_fit(matrix, labels, penalty, dataset.attributes, categories) for penalty in REGULARIZATION_GRID]
    selection = [(validation_score(model), model) for model in models]
    _, model = max(selection, key=lambda candidate: (candidate[0], candidate[1].regularization))
    trained_at = datetime.now(timezone.utc)
    if not any(_informative(query) for query in test):
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
        "training_rows": len(rows),
        "training_customers": len({query.customer_id for query in train if query.can_rerank}),
        "training_positive_rows": int(labels.sum()),
        "validation_selection": [{"regularization": candidate.regularization, "ndcg_at_5": score}
                                 for score, candidate in selection],
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
        evaluation_sha256=fingerprint(report), model=model, model_sha256=fingerprint(model.model_dump(mode="json")),
    )
    return artifact, report


def _write_json(path: Path, document: dict) -> str:
    raw = json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False).encode("utf-8")
    path.write_bytes(raw)
    return hashlib.sha256(raw).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__,
        epilog="Use --schema evidence for the export contract. Pin the reviewed export's SHA-256. "
               "Use report_purchase_orders with original lines and evidence hashes, order_return_lines "
               "and legacy RETURN interactions for ordinary product lines, and PURCHASE interactions "
               "with event_time and created_at mapped to occurred_at and observed_at. Supply a cutoff "
               "customer roster with latest submitted surveys or proven absence, and verified active "
               "catalog and Taste snapshots with their observation times. "
               "Run separately per objective. Use --schema activation for the operator approval manifest.",
    )
    parser.add_argument("--schema", choices=("evidence", "activation"))
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--evidence-sha256")
    parser.add_argument("--mode", choices=MODES)
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
    if args.output.resolve().is_relative_to(repository) or args.output.exists():
        parser.error("--output must be a new directory outside the repository")
    try:
        raw = args.evidence.read_bytes()
        if not hmac.compare_digest(hashlib.sha256(raw).hexdigest(), args.evidence_sha256):
            raise DatasetBlocked("EVIDENCE_FILE_CHECKSUM_MISMATCH")
        evidence = EvidenceBundle.model_validate_json(raw)
        if evidence.exported_at > datetime.now(timezone.utc):
            raise DatasetBlocked("EVIDENCE_EXPORT_IN_FUTURE")
        artifact, report = train_and_evaluate(build_dataset(evidence), args.mode, args.model_version)
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
