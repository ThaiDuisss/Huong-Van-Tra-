"""Read-only runtime proof for whole-order splitting and the regenerated outputs."""
import argparse
from collections import defaultdict
from dataclasses import asdict, replace
import hashlib
from importlib import import_module
import json
from pathlib import Path
import sys
from unittest.mock import patch

from offline_benchmark.common.manifest import artifact_path, build_manifest, digest, require_manifest
from offline_benchmark.data.db_audit import read_table_counts
from offline_benchmark.data.db_loader import load_snapshot
from offline_benchmark.metrics.evaluator import evaluate_payload
from offline_benchmark.metrics.reporting import build_summary
from offline_benchmark.runners.common import evaluate, MODELS
from offline_benchmark.split.chronological_split import chronological_split, ordered_orders, verify_split


def customer_evidence(split) -> dict:
    train = ordered_orders(split.train)
    test = ordered_orders(split.test)
    shared = {o.order_id for o in train} & {o.order_id for o in test}
    return {
        "customer_id": split.customer_id, "total_orders": len(train) + len(test),
        "train_orders": len(train), "test_orders": len(test),
        "train_events": len(split.train), "test_events": len(split.test),
        "latest_train_order_time": train[-1].order_time.isoformat(sep=" "),
        "earliest_test_order_time": test[0].order_time.isoformat(sep=" "),
        "shared_order_between_train_test": "YES" if shared else "NO",
    }


def compare_baseline(baseline: dict, summary: dict, snapshot) -> dict:
    old = baseline["summary"]
    # Removing the two added projection fields reconstructs the earlier source fingerprint.
    original_history = [
        {key: value for key, value in asdict(event).items() if key not in {"order_id", "event_type"}}
        for event in sorted(snapshot.purchases, key=lambda e: e.event_id)
    ]
    if digest(original_history) != old["split"]["history_sha256"]:
        raise ValueError("Historical comparison source history changed")
    if old["split"]["catalog_sha256"] != summary["split"]["catalog_sha256"]:
        raise ValueError("Historical comparison catalog changed")
    for filename, expected in baseline["model_source_sha256"].items():
        if hashlib.sha256(Path(filename).read_bytes()).hexdigest() != expected:
            raise ValueError("A model definition changed during the split update")
    formula = Path("offline_benchmark/metrics/ranking_metrics.py")
    if hashlib.sha256(formula.read_bytes()).hexdigest() != baseline["metric_source_sha256"]:
        raise ValueError("A ranking metric formula changed during the split update")
    old_models = {row["model_key"]: row for row in old["models"]}
    rows = []
    for new in summary["models"]:
        previous = old_models[new["model_key"]]
        row = {"model_key": new["model_key"], "model": new["model"]}
        for metric in ("recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5", "catalog_coverage_at_5"):
            row[metric] = {"old": previous[metric], "new": new[metric],
                           "delta": new[metric] - previous[metric]}
        rows.append(row)
    return {"old_split": old["split"], "new_split": summary["split"], "models": rows,
            "source_history_and_catalog_unchanged": True,
            "model_definitions_and_metric_formulas_unchanged": True,
            "delta_policy": "new minus old; multiply ratio deltas by 100 for percentage points"}


def render_comparison(comparison: dict) -> str:
    lines = ["# Event-row versus whole-order benchmark", "",
             "| Model | Old Recall@5 | New Recall@5 | Δ (pp) | Old Precision@5 | New Precision@5 | Δ (pp) | Old NDCG@5 | New NDCG@5 | Old MAP@5 | New MAP@5 | Old Coverage | New Coverage |",
             "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for row in comparison["models"]:
        r, p, n, m, c = (row[key] for key in ("recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5", "catalog_coverage_at_5"))
        lines.append(f"| {row['model']} | {r['old']:.2%} | {r['new']:.2%} | {100*r['delta']:+.2f} | "
                     f"{p['old']:.2%} | {p['new']:.2%} | {100*p['delta']:+.2f} | "
                     f"{n['old']:.6f} | {n['new']:.6f} | {m['old']:.6f} | {m['new']:.6f} | "
                     f"{c['old']:.2%} | {c['new']:.2%} |")
    lines.extend(["", "Values and deltas use the archived full-precision executed results. "
                  "Both split unit and eligibility changed, so this comparison does not isolate their effects.",
                  "Whole-order holdouts prevent training on other items of the held-out basket. "
                  "A lower score can reflect stricter evaluation; it does not mean benchmark quality fell.",
                  "Source history, catalog, model definitions, weights, and metric formulas are unchanged."])
    return "\n".join(lines) + "\n"


def verify_runtime(env_file: Path | None, baseline_name: str | None = None) -> dict:
    before = read_table_counts(env_file)
    snapshot = load_snapshot(env_file)
    splits = chronological_split(snapshot.purchases)
    verify_split(splits)
    manifest = build_manifest(snapshot, splits)
    require_manifest(manifest)
    source = defaultdict(list)
    for event in snapshot.purchases:
        source[event.customer_id.strip()].append(event)
    for split in splits:
        if {e.event_id for e in source[split.customer_id]} != {e.event_id for e in split.train + split.test}:
            raise ValueError("Eligible customer's events were lost or duplicated")

    results = []
    leakage = {}
    mutated = tuple(replace(s, test=tuple(replace(e, product_id="holdout-only-marker")
                                          for e in s.test)) for s in splits)
    train_events = tuple(e for s in splits for e in s.train)
    train_customers = tuple(s.customer_id for s in splits)
    test_ids = {e.event_id for s in splits for e in s.test}
    test_orders = {e.order_id for s in splits for e in s.test}
    for key, class_name in MODELS.items():
        payload = json.loads(artifact_path(f"{key}.json").read_text(encoding="utf-8"))
        result = evaluate_payload(key, payload, snapshot, splits)
        if result != json.loads(artifact_path(f"{key}_metrics.json").read_text(encoding="utf-8")):
            raise ValueError("Saved metrics do not match the newly generated predictions")
        if any(len(row["top5"]) != 5 for row in payload["predictions"]):
            raise ValueError("A live runner failed to return exactly five recommendations")
        results.append(result)
        module = import_module(f"offline_benchmark.models.{key}")
        constructor = getattr(module, class_name)

        def checked_constructor(training):
            if training.purchases != train_events or training.customer_ids != train_customers:
                raise ValueError("A model did not receive the exact common TRAIN population")
            if ({e.event_id for e in training.purchases} & test_ids or
                    {e.order_id for e in training.purchases} & test_orders):
                raise ValueError("A model received held-out events/orders")
            return constructor(training)

        with patch.object(module, class_name, side_effect=checked_constructor) as fitting:
            original = evaluate(key, snapshot.products, splits)
            changed = evaluate(key, snapshot.products, mutated)
            if fitting.call_count != 2:
                raise ValueError("Unexpected model fitting path")
        saved_top5 = {row["customer_id"]: tuple(row["top5"]) for row in payload["predictions"]}
        if ({r.customer_id: r.top5 for r in original} != saved_top5 or
                {r.customer_id: r.top5 for r in changed} != saved_top5):
            raise ValueError("Regenerated rankings differ or TEST influenced scoring")
        leakage[key] = {"exact_train_only_input": True, "mutated_all_test_products_rankings_unchanged": True,
                        "repeated_fit_matches_saved_predictions": True}
        print(f"Live TRAIN-only and TEST-mutation check: {key} PASS", flush=True)

    summary = build_summary(tuple(results))
    if summary != json.loads(artifact_path("benchmark_summary.json").read_text(encoding="utf-8")):
        raise ValueError("Saved summary differs from regenerated evaluation")
    content = next(result for result in results if result["model_key"] == "content_based")
    union = sorted({p for row in content["customers"] for p in row["top5"]})
    if union != content["recommended_product_ids"]:
        raise ValueError("Content coverage numerator differs from actual Top-5 union")
    witnesses = {p: next(row["customer_id"] for row in content["customers"] if p in row["top5"])
                 for p in union}

    # Keep one short example with multi-item orders in BOTH partitions, plus two others.
    candidates = sorted(splits, key=lambda s: (len(ordered_orders(s.train + s.test)), s.customer_id))
    first = next(s for s in candidates if any(len(o.events) > 1 for o in ordered_orders(s.train))
                 and any(len(o.events) > 1 for o in ordered_orders(s.test)))
    selected = (first,) + tuple(s for s in candidates if s.customer_id != first.customer_id)[:2]
    examples = [customer_evidence(s) for s in selected]
    train_order_ids = {e.order_id for e in first.train}
    basket = [{"order_number": i, "order_id": o.order_id,
               "timestamp": o.order_time.isoformat(sep=" "),
               "product_count": len({e.product_id for e in o.events}), "event_count": len(o.events),
               "event_ids": [e.event_id for e in o.events], "product_ids": [e.product_id for e in o.events],
               "partition": "TRAIN" if o.order_id in train_order_ids else "TEST"}
              for i, o in enumerate(ordered_orders(first.train + first.test), 1)]
    legacy_splits = []
    for cid, events in sorted(source.items()):
        if len(events) >= 5:
            events = sorted(events, key=lambda e: (e.event_time, e.event_id))
            boundary = 4 * len(events) // 5
            shared = {e.order_id for e in events[:boundary]} & {e.order_id for e in events[boundary:]}
            legacy_splits.append((cid, len(shared), len({e.product_id for e in events[boundary:]})))
    comparison = compare_baseline(json.loads(artifact_path(baseline_name).read_text(encoding="utf-8")),
                                  summary, snapshot) if baseline_name else None
    final = load_snapshot(env_file)
    after = read_table_counts(env_file)
    if before != after or build_manifest(final, chronological_split(final.purchases)) != manifest:
        raise ValueError("Shared source changed during verification")
    proof = {
        "split": manifest, "all_customers": [customer_evidence(s) for s in splits],
        "examples": examples, "basket_customer_id": first.customer_id, "basket_orders": basket,
        "all_eligible_events_preserved": True, "global_shared_orders": 0,
        "chronology": "latest TRAIN order_time <= earliest TEST order_time for every customer",
        "leakage_checks": leakage,
        "content_coverage": {"numerator": len(union), "denominator": len(snapshot.products),
                             "ratio": len(union) / len(snapshot.products),
                             "source": "union of actual generated Top-5 lists only",
                             "recommended_product_ids": union, "top5_customer_witness_per_product": witnesses},
        "legacy_event_row_diagnostics": {
            "eligible_customers": len(legacy_splits),
            "customers_with_cross_boundary_orders": sum(n > 0 for cid, n, r in legacy_splits),
            "retained_customers_with_legacy_cross_boundary_orders": sum(
                n > 0 for cid, n, r in legacy_splits if cid in train_customers),
            "old_mean_unique_test_products": sum(r for cid, n, r in legacy_splits) / len(legacy_splits),
            "new_mean_unique_test_products": sum(len({e.product_id for e in s.test}) for s in splits) / len(splits),
        },
        "db_before": before, "db_after": after, "db_counts_and_snapshot_unchanged": True,
    }
    artifact_path("order_split_verification.json").write_text(json.dumps(proof, indent=2, allow_nan=False), encoding="utf-8")
    lines = ["# Whole-order split runtime proof", "", json.dumps(manifest, indent=2), "",
             "| Customer | Orders | TRAIN orders | TEST orders | TRAIN events | TEST events | Latest TRAIN time | Earliest TEST time | Shared order |",
             "| --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |"]
    for row in examples:
        lines.append("| " + " | ".join(str(row[key]) for key in row) + " |")
    lines.extend(["", f"Basket example: {first.customer_id}", "",
                  "| Order # | order_id | Timestamp | Product count | Event count | Partition |",
                  "| ---: | --- | --- | ---: | ---: | --- |"])
    for row in basket:
        lines.append(f"| {row['order_number']} | {row['order_id']} | {row['timestamp']} | "
                     f"{row['product_count']} | {row['event_count']} | {row['partition']} |")
    lines.extend(["", "All eligible events preserved. No per-user or global shared order IDs. "
                  "All six live constructor inputs contain exact TRAIN events only; changing every TEST product "
                  "leaves every customer's Top-5 unchanged. DB counts and history/catalog unchanged.", "",
                  f"Content coverage from actual Top-5 union: {len(union)}/{len(snapshot.products)}. "
                  "Per-product Top-5 customer witnesses are saved in order_split_verification.json."])
    artifact_path("ORDER_SPLIT_PROOF.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if comparison:
        artifact_path("order_comparison.json").write_text(json.dumps(comparison, indent=2, allow_nan=False), encoding="utf-8")
        artifact_path("OLD_VS_NEW.md").write_text(render_comparison(comparison), encoding="utf-8")
    return proof


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--baseline", help="Archived historical summary filename within outputs")
    args = parser.parse_args()
    try:
        proof = verify_runtime(args.env_file, args.baseline)
        print(json.dumps({"examples": proof["examples"], "legacy_diagnostics": proof["legacy_event_row_diagnostics"],
                          "content_coverage": {key: proof["content_coverage"][key] for key in ("numerator", "denominator", "ratio")}}, indent=2))
        print("Order-level runtime verification: PASS")
    except Exception as exc:
        message = str(exc) if type(exc) is ValueError else f"{type(exc).__name__}; check source and saved artifacts"
        print(f"Verification stopped: {message}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
