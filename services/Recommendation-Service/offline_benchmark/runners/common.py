import argparse
from importlib import import_module
from pathlib import Path
import sys

from offline_benchmark.common.filtering import matching_hits, top_five
from offline_benchmark.common.manifest import artifact_path, build_manifest, require_manifest
from offline_benchmark.common.reporting import render_report, serialize_reports
from offline_benchmark.common.types import PredictionReport, TrainingInput
from offline_benchmark.data.db_loader import load_snapshot
from offline_benchmark.split.chronological_split import chronological_split, verify_split


MODELS = {
    "popularity": "Popularity",
    "content_based": "ContentBased",
    "item_item_cf": "ItemItemCF",
    "svd": "SVD",
    "cold_start_hybrid": "ColdStartHybrid",
    "personalized_hybrid": "PersonalizedHybrid",
}


def arguments(description: str, prepare: bool = False):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--env-file", type=Path, help="Existing application env file; defaults to .env/process environment")
    if prepare:
        parser.add_argument("--refresh", action="store_true", help="Replace a stale manifest and invalidate previous predictions, metrics, and proofs")
    else:
        parser.add_argument("--customer-id", help="Inspect one eligible customer; fit still uses all eligible TRAIN rows")
        parser.add_argument("--output", help="JSON filename under offline_benchmark/outputs; defaults to the model name")
    return parser.parse_args()


def evaluate(model_key, products, splits, customer_id=None) -> tuple[PredictionReport, ...]:
    verify_split(splits)
    training = TrainingInput(
        tuple(split.customer_id for split in splits), products,
        tuple(event for split in splits for event in split.train),
    )
    # Choose reporting customers before fitting; never shrink the training cohort.
    customers = tuple(split for split in splits if customer_id is None or split.customer_id == customer_id)
    if not customers:
        raise ValueError("No eligible customer matches the request")
    if not products:
        raise ValueError("No active, nondeleted products available")
    module = import_module(f"offline_benchmark.models.{model_key}")
    model = getattr(module, MODELS[model_key])(training)
    # Generate every requested prediction before inspecting TEST rows for Hits.
    predictions = {split.customer_id: top_five(model.scores(split.customer_id)) for split in customers}
    return tuple(
        PredictionReport(
            model.name, split.customer_id, predictions[split.customer_id],
            tuple(event.product_id for event in split.test),
            matching_hits(predictions[split.customer_id], tuple(event.product_id for event in split.test)),
        )
        for split in customers
    )


def run(model_key: str) -> None:
    args = arguments(f"Inspect {MODELS[model_key]} Top-5 / Test / Hits")
    try:
        snapshot = load_snapshot(args.env_file)
        splits = chronological_split(snapshot.purchases)
        manifest = build_manifest(snapshot, splits)
        require_manifest(manifest)
        output = artifact_path(args.output or f"{model_key}.json")
        if output.name == "split_manifest.json":
            raise ValueError("Prediction output cannot overwrite the shared split manifest")
        reports = evaluate(model_key, snapshot.products, splits, args.customer_id)
        output.write_text(serialize_reports(reports, manifest), encoding="utf-8")
        for report in reports:
            print(render_report(report), end="\n\n")
    except Exception as exc:
        # Driver errors can contain usernames/hosts; never render arbitrary errors.
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check shared DB access/schema and dependencies"
        print(f"Benchmark stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None
