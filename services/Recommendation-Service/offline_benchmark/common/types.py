from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True, slots=True)
class PurchaseEvent:
    event_id: str
    customer_id: str | None
    product_id: str
    event_time: datetime
    order_id: str | None
    event_type: str


@dataclass(frozen=True, slots=True)
class PurchaseOrder:
    order_id: str
    order_time: datetime
    events: tuple[PurchaseEvent, ...]


@dataclass(frozen=True, slots=True)
class CatalogProduct:
    product_id: str
    category_code: str | None
    ingredient_codes: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class DatabaseSnapshot:
    purchases: tuple[PurchaseEvent, ...]
    products: tuple[CatalogProduct, ...]


@dataclass(frozen=True, slots=True)
class CustomerSplit:
    customer_id: str
    train: tuple[PurchaseEvent, ...]
    test: tuple[PurchaseEvent, ...]


@dataclass(frozen=True, slots=True)
class TrainingInput:
    """The entire model input contract. Holdout rows cannot enter through it."""

    customer_ids: tuple[str, ...]
    products: tuple[CatalogProduct, ...]
    purchases: tuple[PurchaseEvent, ...]


@dataclass(frozen=True, slots=True)
class PredictionReport:
    model: str
    customer_id: str
    top5: tuple[str, ...]
    test: tuple[str, ...]
    hits: tuple[str, ...]
