"""Separate V1 verification; never refreshes or rewrites historical benchmarks.

--cached-only verifies binary weight changes from saved component scores.
Without it, also fits the exact production TF-IDF/CF scorer using canonical TRAIN
and current catalog metadata in a read-only DB snapshot. Failure to connect leaves
TF-IDF explicitly NOT_RUN and exits 2, while preserving valid cached evidence.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
from time import perf_counter

from sqlalchemy.exc import OperationalError

from app.recommendation.v1_data import readonly_session
from app.recommendation.v1_model import PurchaseCount, build_artifact, hybrid_scores
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory, V1RecommendationRepository
from app.service.recommendation_service import RecommendationService
from offline_benchmark.common.filtering import matching_hits, top_five
from offline_benchmark.common.manifest import OUTPUTS, build_manifest, digest
from offline_benchmark.common.types import PredictionReport
from offline_benchmark.data.db_loader import read_snapshot
from offline_benchmark.metrics.evaluator import evaluate_reports
from offline_benchmark.models.base import blend
from offline_benchmark.split.chronological_split import chronological_split, ordered_orders


NEW_FILES = {"v1_60_40_verification.json", "V1_60_40_VERIFICATION.md"}


def protected_hashes() -> dict[str, str]:
    # The new report is the only output this command may replace.
    files = [p for p in OUTPUTS.iterdir() if p.is_file() and p.name not in NEW_FILES]
    for folder in ("models", "split", "metrics"):
        files.extend((OUTPUTS.parent / folder).glob("*.py"))
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(files)}


def weight_only(manifest, summary, payload, components):
    if payload["split"] != manifest or components["split"] != manifest or summary["split"] != manifest:
        raise ValueError("Cached components and historical predictions do not share one split")
    ids = tuple(summary["catalog_product_ids"])
    customers = components["components"]["customers"]
    old, new = [], []
    changed = 0
    for row in payload["predictions"]:
        user = row["customer_id"]
        values = customers[user]
        if set(values["content"]) != set(ids) or set(values["cf"]) != set(ids):
            raise ValueError("Cached component catalog differs")
        historical = top_five(blend(values["content"], values["cf"]))
        if historical != tuple(row["top5"]):
            raise ValueError("Cached 50/50 does not reproduce historical rankings")
        scores, flat = hybrid_scores(values["content"], values["cf"], ids)
        if flat:
            scores = components["components"]["popularity"]
        ranking = top_five(scores)
        changed += ranking != historical
        test = tuple(row["test"])
        old.append(PredictionReport("Historical binary 50/50", user, historical, test, matching_hits(historical, test)))
        new.append(PredictionReport("Binary weight-only 60/40", user, ranking, test, matching_hits(ranking, test)))
    old_metrics = evaluate_reports(tuple(old), ids)
    expected = next(m for m in summary["models"] if m["model_key"] == "personalized_hybrid")
    for key, value in old_metrics["aggregate"].items():
        if expected[key] != value:
            raise ValueError("Historical metric summary does not reproduce")
    result = evaluate_reports(tuple(new), ids)
    result.update({"status": "PASS", "source": "saved binary component cache, not a live refit",
                   "weights": {"content": .6, "cf": .4}, "exclude_purchased": False,
                   "changed_ordered_top5_customers": changed})
    return old_metrics, result


def evaluate_tfidf(snapshot, splits, products):
    if tuple(p.product_id for p in products) != tuple(p.product_id for p in snapshot.products):
        raise ValueError("Runtime and canonical catalog IDs differ")
    if any(p.category_code != q.category_code or p.ingredient_codes != q.ingredient_codes
           for p, q in zip(products, snapshot.products)):
        raise ValueError("Runtime/canonical category or BOM metadata differs")
    train = tuple(e for split in splits for e in split.train)
    pair_counts = Counter((e.customer_id, e.product_id) for e in train)
    started = perf_counter()
    model = build_artifact(products, tuple(PurchaseCount(u, p, n) for (u, p), n in sorted(pair_counts.items())))
    build_seconds = perf_counter() - started
    histories = {s.customer_id: CustomerPurchaseHistory(len(ordered_orders(s.train)), dict(
        Counter(e.product_id for e in s.train))) for s in splits}

    class SnapshotRepository:
        def customer_history(self, db, customer_id): return histories[customer_id]
        def active_catalog(self, db): return products
        def popularity(self, db, purchased, limit):
            ranked = sorted(zip(products, model.popularity), key=lambda pair: (-pair[1], pair[0].product_id))
            return [(p.product_id, p.product_code, p.name, float(n)) for p, n in ranked
                    if p.product_id not in purchased][:limit]

    class SnapshotStore:
        def load(self): return model

    service = RecommendationService(SnapshotStore(), SnapshotRepository())
    pure_predictions, routed_predictions = {}, {}
    sources = Counter()
    # Generate all predictions BEFORE using any TEST labels.
    for split in splits:
        user = split.customer_id
        content, cf = model.components(histories[user].product_counts)
        scores, both_flat = hybrid_scores(content, cf, model.product_ids)
        if both_flat:
            scores = dict(zip(model.product_ids, model.popularity))
        pure_predictions[user] = top_five(scores)
        response = service.recommend(None, user, 5, exclude_purchased=False)
        routed_predictions[user] = tuple(item.product_id for item in response.items)
        sources[response.source] += 1

    def result(name, predictions):
        reports = tuple(PredictionReport(name, s.customer_id, predictions[s.customer_id],
                                         tuple(e.product_id for e in s.test),
                                         matching_hits(predictions[s.customer_id], tuple(e.product_id for e in s.test)))
                        for s in splits)
        return {"status": "PASS", "exclude_purchased": False,
                "training_events": len(train), "training_event_ids_sha256": digest(sorted(e.event_id for e in train)),
                **evaluate_reports(reports, model.product_ids)}

    pure = result("Production TF-IDF Content + CF 60/40 (model-only)", pure_predictions)
    pure["build_seconds"] = build_seconds
    pure["features"] = len(model.vocabulary)
    routed = result("V1 routing using distinct TRAIN orders", routed_predictions)
    routed["sources"] = dict(sources)
    return pure, routed


def deltas(old, new):
    return {key: new[key] - old[key] for key in (
        "recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5", "catalog_coverage_at_5",
        "total_hits", "unique_recommended_products")}


def render(payload):
    lines = ["# Separate V1 60/40 verification", "",
             "Historical canonical 50/50 outputs and research outputs are preserved.", "",
             "| Variant | Status | Recall@5 | Precision@5 | NDCG@5 | MAP@5 | Coverage | Hits | Unique products |",
             "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for key, label in (("historical_binary_50_50", "Historical binary50/50"),
                       ("binary_weight_only_60_40", "Binary weight-only60/40"),
                       ("full_tfidf_60_40", "Production TF-IDF60/40, model-only"),
                       ("tfidf_v1_routing", "Production V1 routing, TRAIN order counts")):
        result = payload[key]
        a = result.get("aggregate")
        if a:
            lines.append(f"| {label} | {result.get('status', 'PASS')} | {a['recall_at_5']:.6%} | "
                         f"{a['precision_at_5']:.6%} | {a['ndcg_at_5']:.6f} | {a['map_at_5']:.6f} | "
                         f"{a['catalog_coverage_at_5']:.6%} | {a['total_hits']} | {a['unique_recommended_products']} |")
        else:
            lines.append(f"| {label} | {result['status']} | - | - | - | - | - | - | - |")
    lines.extend(["", "## Binary weight-only deltas (new minus historical)", "",
                  "```json", json.dumps(payload["binary_deltas"], indent=2), "```", "",
                  f"Changed ordered Top-5 customers: {payload['binary_weight_only_60_40']['changed_ordered_top5_customers']}.",
                  "", "Weight-only uses saved binary components; it is not a TF-IDF result.",
                  "Model-only evaluates Hybrid on the common cohort regardless of TRAIN order count. "
                  "The routing variant calls the actual serving service with each customer's TRAIN order count; "
                  "customers with 0-4 TRAIN orders use Popularity. Neither fit consumes TEST rows.", "",
                  "Canonical split/TEST/catalog fingerprints remain identical. Repeats are allowed in this "
                  "comparison (exclude_purchased=false), matching the historical evaluation protocol.", "",
                  "## TF-IDF execution status", "",
                  json.dumps(payload["full_tfidf_60_40"].get("reason", "Executed on read-only canonical DB snapshot")),
                  "", "## Reproduce", "", "```powershell",
                  "Set-Location 'E:\\Huong-Van-Tra-\\services\\Recommendation-Service'",
                  "& '.\\.venv\\Scripts\\python.exe' -B -m offline_benchmark.runners.verify_v1_60_40 --cached-only",
                  "& '.\\.venv\\Scripts\\python.exe' -B -m offline_benchmark.runners.verify_v1_60_40 --env-file recommend.env",
                  "```", ""])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--cached-only", action="store_true")
    args = parser.parse_args()
    before = protected_hashes()
    read = lambda name: json.loads((OUTPUTS / name).read_text(encoding="utf-8"))
    manifest, summary = read("split_manifest.json"), read("benchmark_summary.json")
    expected = {"eligible_customer_count": 583, "train_order_count": 6668,
                "test_order_count": 1947, "active_product_count": 64}
    if any(manifest[key] != value for key, value in expected.items()):
        raise ValueError("The selected historical benchmark is not the required V1 cohort")
    historical, binary = weight_only(manifest, summary, read("personalized_hybrid.json"),
                                     read("routing_component_scores.json"))
    pending = {"status": "NOT_RUN", "reason": "Cached-only mode: live metadata/TRAIN snapshot not loaded"}
    payload = {"contract": "separate-v1-60-40-verification", "created_at": datetime.now(timezone.utc).isoformat(),
               "canonical_split": manifest, "historical_binary_50_50": historical,
               "binary_weight_only_60_40": binary, "full_tfidf_60_40": dict(pending),
               "tfidf_v1_routing": dict(pending),
               "binary_deltas": deltas(historical["aggregate"], binary["aggregate"])}
    live_failed = False
    if not args.cached_only:
        try:
            with readonly_session(args.env_file) as (db, settings):
                snapshot = read_snapshot(db)
                products = V1RecommendationRepository().active_catalog(db)
                missing = V1RecommendationRepository().missing_purchase_order_events(db)
            splits = chronological_split(snapshot.purchases)
            if build_manifest(snapshot, splits) != manifest:
                raise ValueError("Live DB differs from historical canonical fingerprint; outputs preserved")
            full, routed = evaluate_tfidf(snapshot, splits, products)
            changed = tuple(replace_test_products(split) for split in splits)
            repeated, repeated_routing = evaluate_tfidf(snapshot, changed, products)
            for original, other in ((full, repeated), (routed, repeated_routing)):
                if [r["top5"] for r in original["customers"]] != [r["top5"] for r in other["customers"]]:
                    raise ValueError("TEST labels influenced predictions")
            payload.update({"full_tfidf_60_40": full, "tfidf_v1_routing": routed,
                            "tfidf_deltas": deltas(historical["aggregate"], full["aggregate"]),
                            "live_missing_purchase_order_events": missing,
                            "test_label_mutation_rankings_unchanged": True})
        except OperationalError as exc:
            args_original = getattr(exc.orig, "args", ())
            pending = {"status": "NOT_RUN", "reason": "Live DB unavailable; no TF-IDF cohort result claimed",
                       "exception": type(exc).__name__,
                       "driver_code": args_original[0] if args_original and isinstance(args_original[0], int) else None}
            payload["full_tfidf_60_40"] = pending
            payload["tfidf_v1_routing"] = dict(pending)
            live_failed = True
    if protected_hashes() != before:
        raise ValueError("Historical benchmark files changed during verification")
    payload["existing_benchmark_files_unchanged"] = True
    payload["historical_prediction_sha256"] = digest(read("personalized_hybrid.json"))
    for name, content in (("v1_60_40_verification.json", json.dumps(payload, indent=2, allow_nan=False)),
                          ("V1_60_40_VERIFICATION.md", render(payload))):
        (OUTPUTS / name).write_text(content, encoding="utf-8")
    print(render(payload))
    if live_failed:
        raise SystemExit(2)


def replace_test_products(split):
    from dataclasses import replace
    return replace(split, test=tuple(replace(e, product_id="held-out-only") for e in split.test))


if __name__ == "__main__":
    main()
