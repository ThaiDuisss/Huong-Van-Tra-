from dataclasses import dataclass
from datetime import datetime, timezone
from math import isfinite, log1p
from typing import Literal

from app.recommendation.v1_model import CatalogProduct
from app.repositories.explore_history_repository import ExploreHistory
from app.service.personalized_taste_features import ProductTasteFeatures, TasteFeatureBatch

Mode = Literal["EXPLORE", "FAMILIAR"]
MODES: tuple[Mode, ...] = ("EXPLORE", "FAMILIAR")
MIN_VALID_ORDERS = 5
FEATURE_SCHEMA = "taste-reranker-features-v1"
CATALOG_CONTRACT = "taste-projection-v1-verified-composition-single-variant-preparation"
FEATURE_COLUMNS = (
    "hybrid", "tfidf", "item_cf", "log_retained_lines", "log_recency_days", "recency_missing",
    "log_valid_orders", "log_sellable_variants", "variant_count_missing", "profile_available",
    "taste_available",
)


class FeatureError(ValueError):
    pass


@dataclass(frozen=True, slots=True)
class RankingFeatures:
    product_id: str
    hybrid: float
    tfidf: float
    item_cf: float
    has_signal: bool
    frequency: int
    recency_days: float | None
    valid_orders: int
    category: str | None
    sellable_variants: int | None
    taste: ProductTasteFeatures | None

    @property
    def has_taste(self) -> bool:
        return bool(self.taste and any(variant.has_observation for variant in self.taste.variants))

    @property
    def needs_aggregation(self) -> bool:
        return bool(self.taste and self.taste.variants and
                    (len(self.taste.variants) != 1 or self.sellable_variants != 1))


def eligible_products(mode: Mode, commercial: set[str], history: ExploreHistory) -> set[str]:
    if mode == "FAMILIAR":
        return commercial & history.product_counts.keys()
    return commercial - history.purchased_ids


def ranking_features(product: CatalogProduct, history: ExploreHistory, cutoff: datetime,
                     hybrid: float, tfidf: float, item_cf: float, taste: TasteFeatureBatch) -> RankingFeatures:
    product_id = product.product_id
    last_purchase = (history.last_retained_purchase or {}).get(product_id)
    recency_days = None
    if last_purchase is not None:
        if last_purchase.tzinfo is None:
            last_purchase = last_purchase.replace(tzinfo=timezone.utc)
        recency_days = (cutoff - last_purchase).total_seconds() / 86400
    return RankingFeatures(
        product_id=product_id, hybrid=hybrid, tfidf=tfidf, item_cf=item_cf,
        has_signal=tfidf > 0 or item_cf > 0, frequency=history.product_counts.get(product_id, 0),
        recency_days=recency_days, valid_orders=history.valid_order_count, category=product.category_code,
        sellable_variants=(taste.sellable_variant_counts or {}).get(product_id),
        taste=(taste.products or {}).get(product_id),
    )


def feature_names(attributes: tuple[str, ...], categories: tuple[str, ...]) -> tuple[str, ...]:
    return FEATURE_COLUMNS + tuple(f"category:{code}" for code in categories) + tuple(
        f"{group}:{attribute}:{measurement}"
        for group in ("hard", "soft") for attribute in attributes for measurement in ("value", "missing")
    )


def vector(row: RankingFeatures, attributes: tuple[str, ...], categories: tuple[str, ...]) -> list[float]:
    if (any(not isfinite(value) or value < 0 for value in (row.tfidf, row.item_cf))
            or not isfinite(row.hybrid) or not 0 <= row.hybrid <= 1 + 1e-12
            or row.frequency < 0 or row.valid_orders < MIN_VALID_ORDERS
            or row.sellable_variants is not None and row.sellable_variants < 0
            or row.recency_days is not None and (not isfinite(row.recency_days) or row.recency_days < 0)
            or (row.frequency > 0) != (row.recency_days is not None)):
        raise FeatureError("INVALID_RANKING_FEATURES")
    if row.needs_aggregation:
        raise FeatureError("VARIANT_PREPARATION_AGGREGATION_UNAVAILABLE")

    values = [
        row.hybrid, row.tfidf, row.item_cf, log1p(row.frequency), log1p(row.recency_days or 0),
        float(row.recency_days is None), log1p(row.valid_orders), log1p(row.sellable_variants or 0),
        float(row.sellable_variants is None), float(bool(row.taste and row.taste.variants)), float(row.has_taste),
    ]
    values.extend(float(row.category == category) for category in categories)
    variant = row.taste.variants[0] if row.taste and row.taste.variants else None
    for group in (variant.hard_matches if variant else {}, variant.soft_compatibility if variant else {}):
        if group.keys() - set(attributes):
            raise FeatureError("UNSUPPORTED_TASTE_ATTRIBUTES")
        for attribute in attributes:
            value = group.get(attribute)
            if value is not None and (not isfinite(value) or not 0 <= value <= 1):
                raise FeatureError("INVALID_TASTE_COMPATIBILITY")
            values.extend((float(value or 0), float(value is None)))
    return values
