"""Immutable boundary between prediction and held-out labels."""
from dataclasses import asdict, dataclass
from datetime import datetime
import hashlib
import json

from offline_benchmark.common.types import PurchaseEvent

PROTOCOL = "causal-canonical-train-only-customer-holdout-v1"
MODE = "UNFILTERED_BENCHMARK"
K = 5
CATALOG_POLICY = "frozen-retrospective-active-catalog-no-historical-availability"


def digest(value) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                    separators=(",", ":"), allow_nan=False).encode("utf-8")).hexdigest()


def customer_reference(customer_id: str) -> str:
    return "KH-" + hashlib.sha256(customer_id.encode("utf-8")).hexdigest()[:16].upper()


@dataclass(frozen=True, slots=True)
class Opportunity:
    customer_id: str  # Internal only, never serialized to workbook or predictions.
    cutoff: datetime
    train: tuple[PurchaseEvent, ...]

    @property
    def customer(self):
        return customer_reference(self.customer_id)

    @property
    def reference(self):
        return "OFF-" + digest([PROTOCOL, self.customer, self.cutoff.isoformat(),
                                 [e.event_id for e in self.train]])[:24].upper()


@dataclass(frozen=True, slots=True)
class RankedItem:
    rank: int
    product_id: str
    score: float


@dataclass(frozen=True, slots=True)
class Prediction:
    opportunity_ref: str
    customer: str
    cutoff: str
    items: tuple[RankedItem, ...]
    route: str
    fallback_reason: str | None
    state_sha256: str
    state_event_count: int
    state_order_count: int
    state_events_sha256: str
    candidate_ids: tuple[str, ...]
    future_orders_excluded: int
    spanning_orders_excluded: int
    mode: str = MODE
    protocol: str = PROTOCOL


@dataclass(frozen=True, slots=True)
class FrozenPredictions:
    predictions: tuple[Prediction, ...]
    sha256: str

    @classmethod
    def freeze(cls, predictions):
        values = tuple(predictions)
        refs = [p.opportunity_ref for p in values]
        customers = [p.customer for p in values]
        if len(refs) != len(set(refs)) or len(customers) != len(set(customers)):
            raise ValueError("Duplicate offline opportunities/customers")
        return cls(values, digest([asdict(p) for p in values]))

    def verify(self):
        if self != self.freeze(self.predictions):
            raise ValueError("Predictions differ from the pre-evaluation freeze")


@dataclass(frozen=True, slots=True)
class EvaluationRow:
    prediction: Prediction
    ground_truth: tuple[str, ...]
    hits: tuple[str, ...]
    test_order_products: tuple[tuple[str, tuple[str, ...]], ...]
    status: str
    metrics: tuple[tuple[str, float], ...]


@dataclass(frozen=True, slots=True)
class EvaluationResult:
    frozen: FrozenPredictions
    rows: tuple[EvaluationRow, ...]
    metrics_json: str

    @property
    def metrics(self):
        return json.loads(self.metrics_json)
