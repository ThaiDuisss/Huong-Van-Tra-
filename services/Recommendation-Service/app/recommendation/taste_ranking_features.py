from collections.abc import Mapping
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from math import isfinite, log1p
from statistics import mean, median
from typing import Literal

from app.recommendation.v1_model import CatalogProduct, hybrid_scores
from app.repositories.explore_history_repository import ExploreHistory
from app.service.personalized_taste_features import ProductTasteFeatures, TasteFeatureBatch

Mode = Literal["EXPLORE", "FAMILIAR"]
MODES: tuple[Mode, ...] = ("EXPLORE", "FAMILIAR")
RankingFeatureSet = Literal["BEHAVIORAL_ONLY", "BEHAVIORAL_PLUS_TASTE"]
RANKING_FEATURE_SETS: tuple[RankingFeatureSet, ...] = ("BEHAVIORAL_ONLY", "BEHAVIORAL_PLUS_TASTE")
MIN_VALID_ORDERS = 5
FEATURE_SCHEMA = "taste-reranker-features-v2"
CATALOG_CONTRACT = "pos-single-store-stock-same-preparation-v1"
RETURN_REASONS = ("CHANGE_OF_MIND", "DEFECTIVE", "DAMAGED", "WRONG_ITEM", "QUALITY_ISSUE", "EXPIRED", "OTHER")
REPEAT_COLUMNS = (
    "log_retained_orders", "log_repeat_interval_days", "repeat_interval_missing",
    "signed_log_cycle_deviation", "log_retained_net_quantity", "log_returned_lines",
    "returned_line_rate", "repeat_evidence_missing", "log_unknown_reason_returns",
) + tuple(f"log_return:{reason.lower()}" for reason in RETURN_REASONS)
FEATURE_COLUMNS = (
    "hybrid", "tfidf", "item_cf", "log_retained_lines", "log_recency_days", "recency_missing",
    "log_valid_orders", "log_sellable_variants", "variant_count_missing", "profile_available",
    "taste_available",
) + REPEAT_COLUMNS


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
    retained_order_frequency: int | None = None
    repeat_interval_days: float | None = None
    cycle_deviation_days: float | None = None
    net_quantity: float | None = None
    returned_lines: int | None = None
    returned_line_rate: float | None = None
    return_reasons: dict[str, int] = field(default_factory=dict)
    unknown_reason_returns: int | None = None

    @property
    def has_taste(self) -> bool:
        return any(value is not None for group in taste_groups(self) for value in group.values())

    @property
    def needs_aggregation(self) -> bool:
        return bool(self.taste and len({(v.preparation_code, v.preparation_sha256)
                                       for v in self.taste.variants}) > 1)


def taste_groups(row: RankingFeatures) -> tuple[dict[str, float], dict[str, float]]:
    # Average observed measurements only within one preparation. Keep conflicting
    # preparations missing for this Product; other Products can still rerank.
    if not row.taste or row.needs_aggregation:
        return {}, {}
    groups = []
    for field_name in ("hard_matches", "soft_compatibility"):
        observed = {}
        for variant in row.taste.variants:
            for attribute, value in getattr(variant, field_name).items():
                if value is not None:
                    if not isfinite(value) or not 0 <= value <= 1:
                        raise FeatureError("INVALID_TASTE_COMPATIBILITY")
                    observed.setdefault(attribute, []).append(value)
        groups.append({key: float(mean(values)) for key, values in observed.items()})
    return groups[0], groups[1]


def has_behavior_signal(tfidf: float, item_cf: float) -> bool:
    return tfidf > 0 or item_cf > 0


def level2_hybrid_scores(content: Mapping[str, float], cf: Mapping[str, float],
                         ids: tuple[str, ...]) -> tuple[dict[str, float], bool]:
    scores, _ = hybrid_scores(content, cf, ids)
    # A constant candidate range normalizes to zero. Preserve the tie and the
    # original signal instead of manufacturing a score or dropping the result.
    return scores, any(has_behavior_signal(content[p], cf[p]) for p in ids)


def eligible_products(mode: Mode, commercial: set[str], history: ExploreHistory) -> set[str]:
    if mode == "FAMILIAR":
        return commercial & history.product_counts.keys()
    return commercial - history.purchased_ids


def ranking_features(product: CatalogProduct, history: ExploreHistory, cutoff: datetime,
                     hybrid: float, tfidf: float, item_cf: float, taste: TasteFeatureBatch,
                     available_variants: set[str] | None = None) -> RankingFeatures:
    product_id = product.product_id
    last_purchase = (history.last_retained_purchase or {}).get(product_id)
    recency_days = None
    if last_purchase is not None:
        if last_purchase.tzinfo is None:
            last_purchase = last_purchase.replace(tzinfo=timezone.utc)
        recency_days = (cutoff - last_purchase).total_seconds() / 86400
    stats = history.product_history.get(product_id)
    product_taste = (taste.products or {}).get(product_id)
    if product_taste and available_variants is not None:
        product_taste = replace(product_taste, variants=tuple(
            variant for variant in product_taste.variants if variant.variant_id in available_variants
        ))
    interval = None
    if stats:
        dates = sorted(set(stats.retained_order_times))
        if len(dates) >= 2:
            interval = median((right - left).total_seconds() / 86400 for left, right in zip(dates, dates[1:]))
    return RankingFeatures(
        product_id=product_id, hybrid=hybrid, tfidf=tfidf, item_cf=item_cf,
        has_signal=has_behavior_signal(tfidf, item_cf), frequency=history.product_counts.get(product_id, 0),
        recency_days=recency_days, valid_orders=history.valid_order_count, category=product.category_code,
        sellable_variants=(len(available_variants) if available_variants is not None
                           else (taste.sellable_variant_counts or {}).get(product_id)),
        taste=product_taste,
        retained_order_frequency=len(stats.retained_order_times) if stats else None,
        repeat_interval_days=interval,
        cycle_deviation_days=recency_days - interval if recency_days is not None and interval is not None else None,
        net_quantity=float(stats.net_quantity) if stats else None,
        returned_lines=stats.returned_lines if stats else None,
        returned_line_rate=stats.returned_lines / stats.purchased_lines if stats else None,
        return_reasons=stats.return_reasons if stats else {},
        unknown_reason_returns=(stats.unknown_reason_returns + sum(
            count for reason, count in stats.return_reasons.items() if reason not in RETURN_REASONS
        ) if stats else None),
    )


def feature_names(attributes: tuple[str, ...], categories: tuple[str, ...]) -> tuple[str, ...]:
    return FEATURE_COLUMNS + tuple(f"category:{code}" for code in categories) + tuple(
        f"{group}:{attribute}:{measurement}"
        for group in ("hard", "soft") for attribute in attributes for measurement in ("value", "missing")
    )


def taste_column_indices(names: tuple[str, ...] | list[str]) -> tuple[int, ...]:
    return tuple(i for i, name in enumerate(names) if name in {"profile_available", "taste_available"}
                 or name.startswith(("hard:", "soft:")))


def vector(row: RankingFeatures, attributes: tuple[str, ...], categories: tuple[str, ...]) -> list[float]:
    if (any(not isfinite(value) or value < 0 for value in (row.tfidf, row.item_cf))
            or not isfinite(row.hybrid) or not 0 <= row.hybrid <= 1 + 1e-12
            or row.frequency < 0 or row.valid_orders < MIN_VALID_ORDERS
            or row.sellable_variants is not None and row.sellable_variants < 0
            or row.recency_days is not None and (not isfinite(row.recency_days) or row.recency_days < 0)
            or (row.frequency > 0) != (row.recency_days is not None)):
        raise FeatureError("INVALID_RANKING_FEATURES")
    deviation = row.cycle_deviation_days or 0
    values = [
        row.hybrid, row.tfidf, row.item_cf, log1p(row.frequency), log1p(row.recency_days or 0),
        float(row.recency_days is None), log1p(row.valid_orders), log1p(row.sellable_variants or 0),
        float(row.sellable_variants is None), float(bool(row.taste and row.taste.variants)), float(row.has_taste),
        log1p(row.retained_order_frequency or 0), log1p(row.repeat_interval_days or 0),
        float(row.repeat_interval_days is None),
        log1p(deviation) if deviation >= 0 else -log1p(-deviation),
        log1p(row.net_quantity or 0), log1p(row.returned_lines or 0), row.returned_line_rate or 0.0,
        float(row.retained_order_frequency is None), log1p(row.unknown_reason_returns or 0),
    ]
    values.extend(log1p(row.return_reasons.get(reason, 0)) for reason in RETURN_REASONS)
    values.extend(float(row.category == category) for category in categories)
    for group in taste_groups(row):
        if group.keys() - set(attributes):
            raise FeatureError("UNSUPPORTED_TASTE_ATTRIBUTES")
        for attribute in attributes:
            value = group.get(attribute)
            if value is not None and (not isfinite(value) or not 0 <= value <= 1):
                raise FeatureError("INVALID_TASTE_COMPATIBILITY")
            values.extend((float(value or 0), float(value is None)))
    return values
