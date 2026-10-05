from dataclasses import asdict
import hashlib
import json
from pathlib import Path

from offline_benchmark.common.types import CustomerSplit, DatabaseSnapshot
from offline_benchmark.split.chronological_split import ordered_orders, verify_split


WORKSPACE = Path(__file__).resolve().parents[1]
OUTPUTS = WORKSPACE / "outputs"
MANIFEST_PATH = OUTPUTS / "split_manifest.json"


def digest(value) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), default=str).encode()
    return hashlib.sha256(encoded).hexdigest()


def build_manifest(snapshot: DatabaseSnapshot, splits: tuple[CustomerSplit, ...]) -> dict:
    verify_split(splits)
    total_customers = len({(event.customer_id or "").strip() for event in snapshot.purchases
                           if (event.customer_id or "").strip()})
    train_orders = sum(len(ordered_orders(split.train)) for split in splits)
    test_orders = sum(len(ordered_orders(split.test)) for split in splits)
    train_events = sum(len(split.train) for split in splits)
    test_events = sum(len(split.test) for split in splits)
    total_orders = train_orders + test_orders
    total_events = train_events + test_events
    return {
        "contract": "purchase-order-chronological-80-20-v2",
        "minimum_purchase_orders": 5,
        "order_time_policy": "MIN(event_time) within each real order_id",
        "ordering": ["order_time", "interaction_events.order_id"],
        "within_order_ordering": ["event_time", "interaction_events.id"],
        "total_customer_count": total_customers,
        "eligible_customer_count": len(splits),
        "excluded_customer_count": total_customers - len(splits),
        "eligible_order_count": total_orders,
        "train_order_count": train_orders,
        "test_order_count": test_orders,
        "eligible_event_count": total_events,
        "train_event_count": train_events,
        "test_event_count": test_events,
        "train_order_ratio": train_orders / total_orders if total_orders else 0.0,
        "test_order_ratio": test_orders / total_orders if total_orders else 0.0,
        "train_event_ratio": train_events / total_events if total_events else 0.0,
        "test_event_ratio": test_events / total_events if total_events else 0.0,
        "active_product_count": len(snapshot.products),
        "test_leakage": "NO",
        "shared_order_between_train_test": "NO",
        "history_sha256": digest([
            asdict(event) for event in sorted(snapshot.purchases, key=lambda event: event.event_id)
        ]),
        "catalog_sha256": digest([
            asdict(product) for product in sorted(snapshot.products, key=lambda p: p.product_id)
        ]),
        "split_sha256": digest({"contract": "purchase-order-chronological-80-20-v2", "customers": [
            {"customer_id": s.customer_id,
             "train_order_ids": [order.order_id for order in ordered_orders(s.train)],
             "test_order_ids": [order.order_id for order in ordered_orders(s.test)],
             "train_ids": [event.event_id for event in s.train],
             "test_ids": [event.event_id for event in s.test]}
            for s in splits
        ]}),
    }


def require_manifest(current: dict) -> None:
    if not MANIFEST_PATH.is_file():
        raise ValueError("Prepare the common split first: python -m offline_benchmark.runners.prepare_split")
    saved = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if saved != current:
        raise ValueError("Shared DB snapshot changed. Prepare a new split and rerun the models together.")


def artifact_path(filename: str) -> Path:
    if Path(filename).name != filename or filename in {"", ".", ".."}:
        raise ValueError("Output must be a filename inside offline_benchmark/outputs")
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    path = OUTPUTS / filename
    if not path.resolve().is_relative_to(WORKSPACE.resolve()):
        raise ValueError("Output escapes the offline benchmark workspace")
    return path
