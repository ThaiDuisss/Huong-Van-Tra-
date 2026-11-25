"""Assemble a checked Level 2 export from immutable, audited source captures."""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import hmac
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from app.recommendation.taste_ranking_features import MODES, taste_groups
from app.repositories.explore_history_repository import ExploreHistoryError
from app.training.taste_ranking_dataset import (
    SPLITS, Dataset, DatasetBlocked, EvidenceBundle, RankingQuery, build_dataset,
)

MAX_FILE_BYTES = 256 * 1024 * 1024


def read_checked(path: Path, sha256: str) -> bytes:
    with path.open("rb") as stream:
        data = stream.read(MAX_FILE_BYTES + 1)
    if len(data) > MAX_FILE_BYTES:
        raise DatasetBlocked("CAPTURE_TOO_LARGE")
    if not hmac.compare_digest(hashlib.sha256(data).hexdigest(), sha256):
        raise DatasetBlocked("CAPTURE_CHECKSUM_MISMATCH")
    return data


def source_packet_documents(bundle: EvidenceBundle) -> dict:
    document = bundle.model_dump(mode="json")
    windows = document["windows"]

    def at_cutoff(*fields):
        return [{key: window[key] for key in ("cutoff", *fields)} for window in windows]

    return {
        "customer": at_cutoff("customer_roster_as_of", "survey_source", "customers"),
        "order": {"receipts": document["receipts"], "receipt_variant_mapping": document["receipt_variant_mapping"]},
        "returns": document["returns"],
        "interactions": document["interaction_purchases"],
        "catalog": at_cutoff("catalog_as_of", "catalog_observed_at", "catalog_source", "catalog", "catalog_variants"),
        "taste_catalog": at_cutoff("taste_catalog_as_of", "taste_catalog_observed_at", "taste_catalog_source",
                                  "taste_catalog_ttl_seconds", "taste_catalog"),
        "inventory": at_cutoff("availability_as_of", "availability_observed_at", "availability_source", "availability"),
    }


def verify_source_packets(bundle: EvidenceBundle, capture_directory: Path) -> None:
    """Bind normalized evidence to the immutable stream captures."""
    expected = source_packet_documents(bundle)
    if set(bundle.coverage) != set(expected):
        raise DatasetBlocked("INCOMPLETE_SOURCE_LEDGER")
    root = capture_directory.resolve(strict=True)
    for name, source in bundle.coverage.items():
        path = (root / source.source_reference).resolve(strict=True)
        if not path.is_relative_to(root) or not path.is_file():
            raise DatasetBlocked("INVALID_CAPTURE_REFERENCE")
        data = read_checked(path, source.source_sha256)
        if json.loads(data) != expected[name]:
            raise DatasetBlocked("SOURCE_PACKET_CONTENT_MISMATCH")


def _split_readiness(queries: tuple[RankingQuery, ...]) -> dict:
    eligible = [query for query in queries if query.eligible]
    informative = [query for query in eligible if query.informative]
    rows = [sample for query in eligible for sample in query.samples]
    usable_taste = [sample.features for query in eligible if query.can_rerank
                   for sample in query.samples if sample.features.has_signal]
    observed = {}
    for row in usable_taste:
        for name, group in zip(("hard", "soft"), taste_groups(row)):
            for attribute, value in group.items():
                observed.setdefault((name, attribute), set()).add(value)
    positives = sum(sample.label for sample in rows)
    features = [sample.features for sample in rows]
    exclusions = Counter({
        "BELOW_ORDER_THRESHOLD": len(queries) - len(eligible),
        "NO_ELIGIBLE_CANDIDATES": sum(not query.samples for query in eligible),
        "NO_BEHAVIOR_SIGNAL": sum(bool(query.samples) and not query.hybrid_has_signal for query in eligible),
        "PREPARATION_INCOMPATIBLE_ROWS": sum(row.needs_aggregation for row in features),
    })
    return {
        "eligible_customers": len({query.customer_id for query in eligible}),
        "ranking_queries": len(eligible),
        "candidate_rows": len(rows),
        "positives": positives,
        "negatives": len(rows) - positives,
        "informative_queries": len(informative),
        "informative_customers": len({query.customer_id for query in informative}),
        "survey_present_queries": sum(query.survey_present for query in eligible),
        "profile_candidate_rows": sum(bool(row.taste and row.taste.variants) for row in features),
        "taste_observed_rows": sum(row.has_taste for row in features),
        "taste_variation": any(len(values) > 1 for values in observed.values()),
        "missingness": {
            "taste": sum(not row.has_taste for row in features),
            "repeat_interval": sum(row.repeat_interval_days is None for row in features),
            "recency": sum(row.recency_days is None for row in features),
            "return_evidence": sum(row.returned_lines is None for row in features),
        },
        "exclusion_reasons": dict(exclusions),
    }


def readiness(dataset: Dataset, bundle: EvidenceBundle) -> dict:
    modes = {mode: {split: _split_readiness(tuple(
        query for query in dataset.queries if query.mode == mode and query.split == split
    )) for split in SPLITS} for mode in MODES}
    fit_ready = all(mode["TRAIN"]["informative_customers"] >= 2 and mode["TRAIN"]["taste_variation"]
                    and mode["VALIDATION"]["informative_queries"] and mode["TEST"]["informative_queries"]
                    for mode in modes.values())
    status = "DATASET_BLOCKED"
    if fit_ready and dataset.provenance == "VERIFIED_COMMERCIAL":
        status = "CONTRACT_MINIMUM_MET"
    elif fit_ready and dataset.provenance == "SIMULATED_DEMO":
        status = "SIMULATION_CONTRACT_MINIMUM_MET"
    return {
        "status": status,
        "provenance": dataset.provenance,
        "dataset_sha256": dataset.fingerprint,
        "modes": modes,
        "windows": [{
            "split": window.split, "cutoff": window.cutoff.isoformat(),
            "outcome_end": window.outcome_end.isoformat(), "outcome_freeze": window.outcome_freeze.isoformat(),
        } for window in bundle.windows],
        "coverage": {name: ledger.model_dump(mode="json") for name, ledger in bundle.coverage.items()},
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--evidence-sha256", required=True)
    parser.add_argument("--capture-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--simulated-demo", action="store_true", help="Export explicitly labelled isolated simulation")
    args = parser.parse_args()
    source = Path(__file__).resolve()
    repository = next((parent for parent in source.parents if (parent / ".git").exists()), source.parents[2])
    output = args.output.resolve()
    if output.is_relative_to(repository) or output.exists():
        parser.error("--output must be a new directory outside the repository")
    try:
        raw = read_checked(args.evidence, args.evidence_sha256)
        bundle = EvidenceBundle.model_validate_json(raw)
        required = "SIMULATED_DEMO" if args.simulated_demo else "VERIFIED_COMMERCIAL"
        if bundle.provenance != required:
            raise DatasetBlocked("VERIFIED_COMMERCIAL_SOURCE_REQUIRED")
        if not args.simulated_demo and bundle.exported_at > datetime.now(timezone.utc):
            raise DatasetBlocked("EVIDENCE_EXPORT_IN_FUTURE")
        verify_source_packets(bundle, args.capture_directory)
        report = readiness(build_dataset(bundle), bundle)
        report["evidence_sha256"] = args.evidence_sha256
        output.parent.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(dir=output.parent) as temp:
            staging = Path(temp) / "export"
            staging.mkdir()
            (staging / "dataset-manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            if report["status"] != "DATASET_BLOCKED":
                (staging / "evidence.json").write_bytes(raw)
            staging.rename(output)
    except DatasetBlocked as error:
        print(json.dumps({"status": "DATASET_BLOCKED", "reason": str(error)}))
        return 2
    except (OSError, ValueError, ExploreHistoryError):
        print(json.dumps({"status": "DATASET_BLOCKED", "reason": "INVALID_OR_UNREADABLE_EVIDENCE"}))
        return 2
    print(json.dumps({"status": report["status"], "evidence_sha256": args.evidence_sha256}))
    return 2 if report["status"] == "DATASET_BLOCKED" else 0


if __name__ == "__main__":
    raise SystemExit(main())
