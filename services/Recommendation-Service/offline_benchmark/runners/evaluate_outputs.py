import argparse
import json
from pathlib import Path
import sys

from offline_benchmark.common.manifest import artifact_path, build_manifest, digest, require_manifest
from offline_benchmark.data.db_audit import read_table_counts
from offline_benchmark.data.db_loader import load_snapshot
from offline_benchmark.metrics.evaluator import evaluate_payload, MODEL_NAMES
from offline_benchmark.metrics.reporting import build_summary, render_aggregate, render_customer, render_summary
from offline_benchmark.split.chronological_split import chronological_split


def evaluate_existing(env_file: Path | None, model_keys: tuple[str, ...],
                      customer_id: str | None = None) -> tuple[dict, ...]:
    before = read_table_counts(env_file)
    snapshot = load_snapshot(env_file)
    splits = chronological_split(snapshot.purchases)
    manifest = build_manifest(snapshot, splits)
    require_manifest(manifest)
    if customer_id is not None and customer_id not in {split.customer_id for split in splits}:
        raise ValueError("Selected customer is outside the common eligible cohort")
    results = []
    for model_key in model_keys:
        path = artifact_path(f"{model_key}.json")
        if not path.is_file():
            raise ValueError(f"Missing {model_key}.json; run that existing model runner first")
        payload = json.loads(path.read_text(encoding="utf-8"))
        results.append(evaluate_payload(model_key, payload, snapshot, splits))

    # Re-evaluate the same payloads to verify exact full-precision determinism.
    for result in results:
        payload = json.loads(artifact_path(f"{result['model_key']}.json").read_text(encoding="utf-8"))
        repeated = evaluate_payload(result["model_key"], payload, snapshot, splits)
        if result != repeated:
            raise ValueError("Evaluation changed on repetition using the same saved predictions")

    after = read_table_counts(env_file)
    if before != after:
        raise ValueError("Shared DB row counts changed during evaluation; investigate concurrent activity")
    final_snapshot = load_snapshot(env_file)
    if build_manifest(final_snapshot, chronological_split(final_snapshot.purchases)) != manifest:
        raise ValueError("Shared DB history/catalog changed during evaluation; results were not published")

    summary = build_summary(tuple(results)) if len(model_keys) == len(MODEL_NAMES) else None
    for result in results:
        artifact_path(f"{result['model_key']}_metrics.json").write_text(
            json.dumps(result, indent=2, allow_nan=False), encoding="utf-8")
    if summary is not None:
        artifact_path("benchmark_summary.json").write_text(
            json.dumps(summary, indent=2, allow_nan=False), encoding="utf-8")
        artifact_path("BENCHMARK_SUMMARY.md").write_text(render_summary(summary), encoding="utf-8")
    artifact_path("evaluation_db_audit.json").write_text(json.dumps({
        "before": before, "after": after, "unchanged": True,
        "split_sha256": manifest["split_sha256"],
        "evaluated_models": list(model_keys), "deterministic_repeated_evaluation": True,
        "evaluation_sha256": digest(results),
    }, indent=2, allow_nan=False), encoding="utf-8")
    return tuple(results)


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate existing Top-5 outputs without fitting models")
    parser.add_argument("--env-file", type=Path)
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument("--model", choices=MODEL_NAMES, help="Evaluate only one saved model output")
    selection.add_argument("--all", action="store_true", help="Evaluate all six saved outputs (default)")
    parser.add_argument("--customer-id", help="Print one customer's metrics; aggregates still use the full cohort")
    args = parser.parse_args()
    try:
        keys = (args.model,) if args.model else tuple(MODEL_NAMES)
        results = evaluate_existing(args.env_file, keys, args.customer_id)
        for result in results:
            print(render_aggregate(result), end="\n\n")
            if args.customer_id:
                row = next(row for row in result["customers"] if row["customer_id"] == args.customer_id)
                print(render_customer(row), end="\n\n")
        print("Sanity checks and repeated evaluation determinism: PASS")
        print("Shared DB row counts and snapshot unchanged: PASS")
        if len(keys) == len(MODEL_NAMES):
            print("Summary: offline_benchmark/outputs/BENCHMARK_SUMMARY.md")
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check DB access/schema and saved output files"
        print(f"Evaluation stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
