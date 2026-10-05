from dataclasses import asdict
from math import fsum, isfinite

from offline_benchmark.common.filtering import matching_hits
from offline_benchmark.common.manifest import build_manifest, digest
from offline_benchmark.common.types import CustomerSplit, DatabaseSnapshot, PredictionReport
from offline_benchmark.metrics.ranking_metrics import K, ranking_metrics


MODEL_NAMES = {
    "popularity": "Popularity",
    "content_based": "Content-Based",
    "item_item_cf": "Item-Item CF",
    "svd": "SVD",
    "cold_start_hybrid": "Cold-Start Hybrid",
    "personalized_hybrid": "Personalized Hybrid",
}


def test_fingerprint(splits: tuple[CustomerSplit, ...]) -> str:
    return digest([
        {"customer_id": split.customer_id,
         "test_ids": [event.event_id for event in split.test],
         "test_order_ids": [event.order_id for event in split.test],
         "test_products": [event.product_id for event in split.test]}
        for split in sorted(splits, key=lambda split: split.customer_id)
    ])


def validate_predictions(
    model_key: str, payload: dict, snapshot: DatabaseSnapshot,
    splits: tuple[CustomerSplit, ...],
) -> tuple[PredictionReport, ...]:
    """Reject stale, partial, malformed, or altered TEST reports before evaluation."""
    if payload.get("split") != build_manifest(snapshot, splits):
        raise ValueError(f"{model_key}: prediction manifest differs from the current shared split")
    expected_test = {s.customer_id: tuple(e.product_id for e in s.test) for s in splits}
    rows = payload.get("predictions")
    if not isinstance(rows, list):
        raise ValueError(f"{model_key}: missing prediction list")
    reports = []
    customers = set()
    for row in rows:
        if not isinstance(row, dict) or row.get("model") != MODEL_NAMES[model_key]:
            raise ValueError(f"{model_key}: invalid prediction model identity")
        customer = row.get("customer_id")
        if not isinstance(customer, str) or customer not in expected_test or customer in customers:
            raise ValueError(f"{model_key}: unexpected or duplicate customer")
        for field in ("top5", "test", "hits"):
            if not isinstance(row.get(field), list) or any(
                not isinstance(product, str) or not product.strip() for product in row[field]
            ):
                raise ValueError(f"{model_key}: invalid {field} product IDs")
        if tuple(row["test"]) != expected_test[customer]:
            raise ValueError(f"{model_key}: TEST products/order differ from the current shared split")
        reports.append(PredictionReport(row["model"], customer, tuple(row["top5"]),
                                        tuple(row["test"]), tuple(row["hits"])))
        customers.add(customer)
    if customers != set(expected_test):
        raise ValueError(f"{model_key}: predictions do not contain the complete eligible-customer set")
    return tuple(sorted(reports, key=lambda report: report.customer_id))


def evaluate_reports(reports: tuple[PredictionReport, ...], catalog_ids: tuple[str, ...]) -> dict:
    """One canonical evaluator for all strategies; macro averages by customer."""
    catalog = set(catalog_ids)
    if not catalog or len(catalog) != len(catalog_ids):
        raise ValueError("Recommendable catalog must contain nonempty, unique product IDs")
    if any(not isinstance(product, str) or not product.strip() for product in catalog_ids):
        raise ValueError("Recommendable catalog IDs must be nonblank strings")
    if not reports or len({report.customer_id for report in reports}) != len(reports):
        raise ValueError("Evaluation requires nonempty reports with unique customers")
    if len({report.model for report in reports}) != 1:
        raise ValueError("Evaluation must contain exactly one model")

    customers = []
    recommended = set()
    for report in sorted(reports, key=lambda report: report.customer_id):
        metrics = ranking_metrics(report.top5, report.test)
        if not set(report.top5) <= catalog:
            raise ValueError("Recommendation lies outside the current active/nondeleted candidate catalog")
        hits = matching_hits(report.top5, report.test)
        if hits != report.hits:
            raise ValueError("Saved Hits differ from Top-5 intersected with unique TEST products")
        customers.append({
            **asdict(report),
            "top5": list(report.top5),
            "test": list(report.test),
            "hits": list(hits),
            "relevant_test_product_ids": sorted(set(report.test)),
            "unique_relevant_test_items": len(set(report.test)),
            **asdict(metrics),
        })
        recommended.update(report.top5)

    count = len(customers)
    total_hits = sum(len(customer["hits"]) for customer in customers)
    aggregate = {
        "evaluated_customers": count,
        "total_top5_recommendation_slots": sum(len(customer["top5"]) for customer in customers),
        "total_hits": total_hits,
        "mean_hits_per_customer": total_hits / count,
        "unique_recommended_products": len(recommended),
        "recommendable_catalog_products": len(catalog),
        "recall_at_5": fsum(customer["recall_at_5"] for customer in customers) / count,
        "precision_at_5": fsum(customer["precision_at_5"] for customer in customers) / count,
        "ndcg_at_5": fsum(customer["ndcg_at_5"] for customer in customers) / count,
        "map_at_5": fsum(customer["ap_at_5"] for customer in customers) / count,
        "catalog_coverage_at_5": len(recommended) / len(catalog),
    }
    for key in ("recall_at_5", "precision_at_5", "ndcg_at_5", "map_at_5", "catalog_coverage_at_5"):
        if not isfinite(aggregate[key]) or not 0.0 <= aggregate[key] <= 1.0:
            raise ValueError("Invalid aggregate metric")
    return {
        "model": reports[0].model,
        "k": K,
        "relevance_policy": "unique TEST products; empty TEST is an error",
        "catalog_product_ids": sorted(catalog),
        "recommended_product_ids": sorted(recommended),
        "aggregate": aggregate,
        "customers": customers,
    }


def evaluate_payload(model_key: str, payload: dict, snapshot: DatabaseSnapshot,
                     splits: tuple[CustomerSplit, ...]) -> dict:
    reports = validate_predictions(model_key, payload, snapshot, splits)
    result = evaluate_reports(reports, tuple(product.product_id for product in snapshot.products))
    result.update({
        "model_key": model_key,
        "split": build_manifest(snapshot, splits),
        "test_sha256": test_fingerprint(splits),
        "customer_set_sha256": digest(sorted(report.customer_id for report in reports)),
        "prediction_sha256": digest(payload),
    })
    return result
