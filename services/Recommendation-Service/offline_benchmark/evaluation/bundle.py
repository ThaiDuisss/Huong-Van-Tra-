"""Versioned, integrity-pinned deployment bundle. Loading never runs inference."""
import base64
from collections import OrderedDict
from dataclasses import asdict, dataclass, replace
import hashlib
import json
from pathlib import Path
import re
from threading import RLock

from app.recommendation.v1_model import CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT
from app.service.recommendation_service import MIN_HYBRID_PURCHASE_ORDERS
from offline_benchmark.common.manifest import build_manifest
from offline_benchmark.data.exact_tfidf import replay
from offline_benchmark.evaluation.contract import FrozenPredictions, K, MODE, PROTOCOL, Prediction, RankedItem
from offline_benchmark.evaluation.runner import VerifiedDataset, _FrozenArchive, evaluate
from offline_benchmark.evaluation.v1_state import CanonicalTrain, build_opportunities
from offline_benchmark.split.chronological_split import chronological_split, ordered_orders

SCHEMA = "hvtra-ai-report-evaluation-bundle-v1"
PERIOD_POLICY = "whole-test-orders-contained-in-source-calendar-period-v1"
SERVICE = Path(__file__).resolve().parents[2]
PREDICTION_SOURCES = (
    "app/recommendation/v1_model.py", "app/service/recommendation_service.py",
    "offline_benchmark/evaluation/contract.py", "offline_benchmark/evaluation/runner.py",
    "offline_benchmark/evaluation/v1_state.py", "offline_benchmark/split/chronological_split.py",
    "offline_benchmark/metrics/ranking_metrics.py",
)
MAX_BUNDLE_BYTES = 32 * 1024 * 1024


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def strict_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("Duplicate JSON keys in evaluation bundle")
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite JSON")))


def source_hashes():
    return {name: sha256((SERVICE / name).read_bytes()) for name in PREDICTION_SOURCES}


@dataclass(frozen=True, slots=True)
class PeriodResult:
    result: object
    orders_selected: int
    events_selected: int
    orders_outside: int
    orders_spanning_boundary: int


@dataclass(frozen=True, slots=True)
class VerifiedBundle:
    dataset: VerifiedDataset
    frozen: FrozenPredictions
    sha256: str

    def descriptor(self):
        return {"kind": "OFFLINE_EVALUATION", "bundle_schema": SCHEMA,
                "bundle_sha256": self.sha256, "split_sha256": self.dataset.manifest["split_sha256"],
                "prediction_sha256": self.frozen.sha256, "model_contract": CONTRACT_VERSION,
                "protocol": PROTOCOL, "mode": MODE, "period_policy": PERIOD_POLICY}

    def for_period(self, from_date, to_date):
        if from_date > to_date:
            raise ValueError("Invalid reporting period")
        selected, outside, spanning, order_count, event_count = [], 0, 0, 0, 0
        for split in self.dataset.splits:
            orders = []
            for order in ordered_orders(split.test):
                low = min(e.event_time for e in order.events).date()
                high = max(e.event_time for e in order.events).date()
                if from_date <= low and high <= to_date:
                    orders.append(order)
                else:
                    outside += 1
                    spanning += low <= to_date and high >= from_date
            if orders:
                events = tuple(e for order in orders for e in order.events)
                selected.append(replace(split, test=events))
                order_count += len(orders); event_count += len(events)
        customers = {o.customer for o in build_opportunities(tuple(selected))}
        subset = FrozenPredictions.freeze(p for p in self.frozen.predictions if p.customer in customers)
        result = evaluate(subset, tuple(selected), self.dataset.products)
        return PeriodResult(result, order_count, event_count, outside, spanning)


def decode_bundle(raw, expected_sha256):
    if (len(raw) > MAX_BUNDLE_BYTES or not re.fullmatch(r"[0-9a-f]{64}", expected_sha256)
            or sha256(raw) != expected_sha256):
        raise ValueError("Evaluation bundle integrity mismatch")
    value = strict_json(raw)
    if value.get("schema") != SCHEMA or value.get("complete") is not True:
        raise ValueError("Incomplete or unsupported evaluation bundle")
    if (value.get("protocol"), value.get("mode"), value.get("k"), value.get("model_contract"),
            value.get("feature_contract"), value.get("normalization_contract"), value.get("routing_threshold")) != (
            PROTOCOL, MODE, K, CONTRACT_VERSION, FEATURE_CONTRACT, NORMALIZATION_CONTRACT, MIN_HYBRID_PURCHASE_ORDERS):
        raise ValueError("Evaluation bundle is incompatible with current V1")
    if value.get("prediction_source_sha256") != source_hashes():
        raise ValueError("Prediction/evaluation source version differs; rebuild verified bundle")
    snapshot_raw = base64.b64decode(value["source_snapshot_base64"], validate=True)
    if sha256(snapshot_raw) != value["source_archive_sha256"]:
        raise ValueError("Frozen source snapshot fingerprint differs")
    snapshot, products, source = replay(_FrozenArchive(snapshot_raw))
    if (source.get("database") != "recommend_app" or len(snapshot.purchases) > 100000
            or len(products) > 10000 or any(e.event_time.tzinfo is not None for e in snapshot.purchases)):
        raise ValueError("Unsupported archived source/time convention")
    splits = chronological_split(snapshot.purchases)
    original = value["original_split_manifest"]
    if (not splits or original.get("contract") != "purchase-order-chronological-80-20-v2"
            or build_manifest(snapshot, splits) != original):
        raise ValueError("Original canonical split manifest differs")
    if tuple((p.product_id,p.category_code,p.ingredient_codes) for p in products) != tuple(
            (p.product_id,p.category_code,p.ingredient_codes) for p in snapshot.products):
        raise ValueError("Frozen exact/canonical catalog projections differ")
    dataset = VerifiedDataset(snapshot, products, splits, json.dumps(original, sort_keys=True),
                              json.dumps(source, sort_keys=True), value["source_archive_sha256"],
                              value["original_manifest_file_sha256"], "IMMUTABLE_BUNDLE")
    saved = value["frozen_predictions"]
    predictions = tuple(Prediction(**{**p, "items": tuple(RankedItem(**i) for i in p["items"]),
                                     "candidate_ids": tuple(p["candidate_ids"])}) for p in saved["predictions"])
    frozen = FrozenPredictions(predictions, saved["prediction_sha256"]); frozen.verify()
    if frozen.sha256 != value["prediction_sha256"]:
        raise ValueError("Prediction fingerprint differs from producer")
    full = evaluate(frozen, splits, products)
    if full.metrics != value["metrics"]:
        raise ValueError("Frozen evaluation metrics differ")
    # Reconstruct all cutoff inputs without fitting any model or reading serving state.
    plan = build_opportunities(splits)
    training = CanonicalTrain.from_opportunities(plan)
    encoded = {e.event_id: json.dumps(asdict(e) | {"event_time": e.event_time.isoformat()},
               sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
               for order in training.orders for e in order.events}
    by_customer = {p.customer:p for p in predictions}
    for opportunity in plan:
        p = by_customer[opportunity.customer]
        orders, future, spanning = training.at(opportunity.cutoff)
        ids = sorted(e.event_id for order in orders for e in order.events)
        if (sha256(b"[" + b",".join(encoded[i] for i in ids) + b"]") != p.state_events_sha256
                or len(ids) != p.state_event_count or len(orders) != p.state_order_count
                or future != p.future_orders_excluded or spanning != p.spanning_orders_excluded):
            raise ValueError("Causal canonical-TRAIN state evidence differs")
    return VerifiedBundle(dataset, frozen, expected_sha256)


class BundleProvider:
    """Rehash bytes on every access; cache only successfully validated immutable objects."""
    _cache = OrderedDict()
    _lock = RLock()

    def __init__(self, path, expected_sha256):
        self.path = Path(path)
        self.expected_sha256 = expected_sha256

    def load(self):
        if not re.fullmatch(r"[0-9a-f]{64}", self.expected_sha256 or ""):
            raise ValueError("Explicit immutable bundle SHA-256 is required")
        with self.path.open("rb") as stream:
            raw = stream.read(MAX_BUNDLE_BYTES + 1)
        if len(raw) > MAX_BUNDLE_BYTES or sha256(raw) != self.expected_sha256:
            raise ValueError("Evaluation bundle missing, oversized or corrupt")
        version = tuple(source_hashes().items())
        key = (self.expected_sha256, version)
        with self._lock:
            if key not in self._cache:
                verified = decode_bundle(raw, self.expected_sha256)
                self._cache[key] = verified
                if len(self._cache) > 2:
                    self._cache.popitem(last=False)
            self._cache.move_to_end(key)
            return self._cache[key]


def prepare_bundle(run_directory, snapshot_path, archive_sha256, run_manifest_sha256):
    """Internal packaging step; validates producer artifacts and never generates Excel/inference."""
    directory = Path(run_directory)
    manifest_raw = (directory / "manifest.json").read_bytes()
    if sha256(manifest_raw) != run_manifest_sha256:
        raise ValueError("Producer run manifest differs from trusted fingerprint")
    manifest = strict_json(manifest_raw)
    required = ("predictions.json", "metrics.json", "evaluation.json", "membership.json", "state-evidence.json", "verification.json")
    blobs = {}
    for name in required:
        blobs[name] = (directory / name).read_bytes()
        if sha256(blobs[name]) != manifest["artifact_sha256"][name]:
            raise ValueError("Producer artifact integrity mismatch: " + name)
    verification = strict_json(blobs["verification.json"])
    if verification.get("prediction_freeze_verified") is not True or verification.get("canonical_manifest_reproduced") is not True:
        raise ValueError("Producer verification is incomplete")
    snapshot_raw = Path(snapshot_path).read_bytes()
    if sha256(snapshot_raw) != archive_sha256 or archive_sha256 != manifest["source_archive_sha256"]:
        raise ValueError("Producer/source archive identity mismatch")
    producer_sources = {name.replace("\\", "/"): value for name, value in manifest["source_file_sha256"].items()}
    recorded = {name:producer_sources.get(name) for name in PREDICTION_SOURCES}
    if recorded != source_hashes():
        raise ValueError("Producer prediction source version is stale")
    value = {"schema": SCHEMA, "complete": True, "protocol": manifest["protocol"], "mode": manifest["mode"],
             "k": manifest["k"], "model_contract": manifest["model_contract"],
             "feature_contract": manifest["feature_contract"], "normalization_contract": manifest["normalization_contract"],
             "routing_threshold": manifest["routing_min_hybrid_orders"],
             "source_archive_sha256": archive_sha256, "producer_manifest_sha256": run_manifest_sha256,
             "original_manifest_file_sha256": manifest["original_manifest_file_sha256"],
             "original_split_manifest": manifest["original_split_manifest"],
             "prediction_source_sha256": recorded, "prediction_sha256": manifest["prediction_sha256"],
             "source_snapshot_base64": base64.b64encode(snapshot_raw).decode("ascii"),
             "frozen_predictions": strict_json(blobs["predictions.json"]), "metrics": strict_json(blobs["metrics.json"])}
    raw = (json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"), allow_nan=False) + "\n").encode("utf-8")
    decode_bundle(raw, sha256(raw))
    return raw
