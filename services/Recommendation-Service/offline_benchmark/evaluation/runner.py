"""Verify immutable input, freeze predictions, then attach held-out labels."""
from dataclasses import asdict, dataclass
import hashlib
import json
from math import fsum, isfinite
from pathlib import Path
import re

from app.recommendation.v1_model import catalog_fingerprint
from offline_benchmark.common.manifest import build_manifest
from offline_benchmark.data.exact_tfidf import replay
from offline_benchmark.evaluation.contract import (
    CATALOG_POLICY, EvaluationResult, EvaluationRow, FrozenPredictions, K, MODE, PROTOCOL,
    customer_reference,
)
from offline_benchmark.evaluation.v1_state import (
    CanonicalTrain, build_opportunities, build_state, predict,
)
from offline_benchmark.metrics.ranking_metrics import ranking_metrics
from offline_benchmark.split.chronological_split import chronological_split, ordered_orders


@dataclass(frozen=True, slots=True)
class VerifiedDataset:
    snapshot: object
    products: tuple
    splits: tuple
    original_manifest_json: str
    source_audit_json: str
    archive_sha256: str
    manifest_file_sha256: str
    snapshot_path: str

    @property
    def manifest(self):
        return json.loads(self.original_manifest_json)


class _FrozenArchive:
    def __init__(self, content):
        self.content = content

    def read_text(self, encoding):
        return self.content.decode(encoding)


def verify_archive(snapshot_path, manifest_path, expected_sha256):
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("An explicit trusted archive SHA-256 is required")
    raw = Path(snapshot_path).read_bytes()
    checksum = hashlib.sha256(raw).hexdigest()
    if checksum != expected_sha256:
        raise ValueError("Archive differs from the trusted fingerprint")
    manifest_raw = Path(manifest_path).read_bytes()
    saved = json.loads(manifest_raw)
    if saved.get("contract") != "purchase-order-chronological-80-20-v2":
        raise ValueError("Unsupported original split contract")
    snapshot, products, source = replay(_FrozenArchive(raw))
    if source.get("database") != "recommend_app":
        raise ValueError("Unexpected archived database identity")
    if tuple((p.product_id, p.category_code, p.ingredient_codes) for p in products) != tuple(
            (p.product_id, p.category_code, p.ingredient_codes) for p in snapshot.products):
        raise ValueError("Exact V1 catalog differs from canonical catalog")
    if any(e.event_time.tzinfo is not None for e in snapshot.purchases):
        raise ValueError("This archive protocol requires the original naive source time convention")
    splits = chronological_split(snapshot.purchases)
    if not splits or not products or build_manifest(snapshot, splits) != saved:
        raise ValueError("Archive does not reproduce the original canonical manifest")
    build_opportunities(splits)  # Fail closed on ambiguous own-order boundaries before inference.
    return VerifiedDataset(snapshot, products, splits, json.dumps(saved, sort_keys=True),
                           json.dumps(source, sort_keys=True), checksum,
                           hashlib.sha256(manifest_raw).hexdigest(), str(Path(snapshot_path).resolve()))


def freeze_predictions(opportunities, products):
    """Accept only TRAIN opportunities/catalog. No TEST object enters this stage."""
    training = CanonicalTrain.from_opportunities(opportunities)
    predictions = []
    for opportunity in opportunities:
        state = build_state(training, products, opportunity.cutoff)
        predictions.append(predict(opportunity, state))
    frozen = FrozenPredictions.freeze(predictions)
    frozen.verify()
    return frozen


def evaluate(frozen, splits, products):
    frozen.verify()
    expected = {customer_reference(s.customer_id): s for s in splits}
    if len(expected) != len(splits) or {p.customer for p in frozen.predictions} != set(expected):
        raise ValueError("Prediction/ground-truth customer cohorts differ")
    opportunities = {o.customer: o for o in build_opportunities(splits)}
    catalog = {p.product_id for p in products}
    if len(catalog) != len(products) or not catalog:
        raise ValueError("Invalid evaluation catalog")
    rows, all_recommended = [], set()
    for prediction in frozen.predictions:
        opportunity = opportunities[prediction.customer]
        if prediction.opportunity_ref != opportunity.reference or prediction.cutoff != opportunity.cutoff.isoformat():
            raise ValueError("Prediction opportunity differs from canonical TRAIN")
        if (prediction.protocol != PROTOCOL or prediction.mode != MODE
                or prediction.candidate_ids != tuple(sorted(catalog))):
            raise ValueError("Prediction protocol/mode/catalog mismatch")
        if any(i.rank != n or not isfinite(i.score) for n, i in enumerate(prediction.items, 1)):
            raise ValueError("Invalid rank or score in frozen prediction")
        ranked = tuple(i.product_id for i in prediction.items)
        if not set(ranked) <= catalog or len(ranked) > K or len(set(ranked)) != len(ranked):
            raise ValueError("Invalid recommendation candidates")
        split = expected[prediction.customer]
        truth = tuple(sorted({e.product_id for e in split.test}))
        hits = tuple(p for p in ranked if p in truth)
        metrics = asdict(ranking_metrics(ranked, truth)) if truth else {}
        status = ("EMPTY_GROUND_TRUTH" if not truth else "NO_CANDIDATES" if not ranked
                  else "OFFLINE_MATCHED" if hits else "OFFLINE_NO_OVERLAP")
        orders = tuple((o.order_id, tuple(sorted({e.product_id for e in o.events})))
                       for o in ordered_orders(split.test))
        rows.append(EvaluationRow(prediction, truth, hits, orders, status, tuple(metrics.items())))
        all_recommended.update(ranked)
    valid = [dict(r.metrics) for r in rows if r.ground_truth]
    means = {name: fsum(m[name] for m in valid) / len(valid) if valid else None
             for name in ("precision_at_5", "recall_at_5", "ndcg_at_5", "ap_at_5")}
    total_hits = sum(len(r.hits) for r in rows)
    metrics = {"protocol": PROTOCOL, "mode": MODE, "k": K,
               "prediction_sha256": frozen.sha256,
               "total_evaluation_opportunities": len(rows),
               "accuracy_evaluated_opportunities": len(valid),
               "customers_with_nonempty_recommendation": sum(bool(r.prediction.items) for r in rows),
               "empty_recommendation_responses": sum(not r.prediction.items for r in rows),
               "empty_ground_truth_opportunities": sum(not r.ground_truth for r in rows),
               "total_recommendation_items": sum(len(r.prediction.items) for r in rows),
               "total_matched_opportunity_product_pairs": total_hits,
               "precision_at_5": means["precision_at_5"], "recall_at_5": means["recall_at_5"],
               "ndcg_at_5": means["ndcg_at_5"], "map_at_5": means["ap_at_5"],
               "hit_rate_at_5": sum(bool(r.hits) for r in rows if r.ground_truth) / len(valid) if valid else None,
               "unique_recommended_products": len(all_recommended),
               "catalog_products": len(catalog),
               "product_coverage": len(all_recommended) / len(catalog),
               "quantity_status": "NOT_VERIFIED", "test_quantity": None,
               "precision_denominator": 5,
               "relevance": "unique canonical products across all customer TEST orders",
               "empty_ground_truth_policy": "undefined metrics; excluded from accuracy denominator, retained in report",
               "catalog_policy": CATALOG_POLICY, "catalog_sha256": catalog_fingerprint(products)}
    frozen.verify()
    return EvaluationResult(frozen, tuple(rows), json.dumps(metrics, sort_keys=True, allow_nan=False))
