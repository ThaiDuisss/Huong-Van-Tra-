"""Versioned input adapter for the approved Level 2 scorers."""
from collections import Counter
from dataclasses import dataclass
from math import exp, log1p

import numpy as np

from app.recommendation.taste_ranking_features import (
    Mode, RankingFeatures, feature_names, taste_column_indices, taste_groups, vector,
)
from app.repositories.explore_history_repository import ExploreHistory

SCHEMA = "taste-reranker-features-v2 + l203c-prefix-v1"
COMMON = (
    "global_log_pop", "recent30_log_pop", "global_log_category_pop", "customer_category_share",
    "retained_category_share", "pop_cf_interaction", "content_affinity_interaction",
)
EXTRAS = {
    "EXPLORE": ("novel_purchase_line_share", "new_category_order_share"),
    "FAMILIAR": ("recency_interval_ratio", "recency_interval_missing", "cycle_proximity",
                 "frequency_recency_score", "frequency_freshness_interaction"),
}
TASTE = (
    "mean_compatibility", "compatibility_missing", "survey_age_log", "survey_missing",
    "retained_profile_tea_affinity", "history_profile_missing", "past_category_disagreement",
    "past_disagreement_missing", "taste_category_interaction", "taste_mode_interaction",
)
COMPONENTS = ("GLOBAL_POPULARITY_MINMAX_PER_QUERY", "HYBRID_V1",
              "CATEGORY_AFFINITY_EXPLORE_OR_FREQ_RECENCY_FAMILIAR", "OBSERVED_TASTE_MEAN")


def names_for(mode, attributes, categories):
    return (*feature_names(attributes, categories), *COMMON, *EXTRAS[mode], *TASTE)


def behavioral_mask(mode, attributes, categories):
    names = names_for(mode, attributes, categories)
    return (*taste_column_indices(names), *range(len(names) - len(TASTE), len(names)))


@dataclass(frozen=True, slots=True)
class FeatureContext:
    history: ExploreHistory
    orders: tuple[tuple[str, ...], ...]
    categories_by_product: dict[str, str | None]
    tea_type_by_product: dict[str, str]
    approved_tags: dict[str, tuple[str, ...]]
    popularity: Counter
    recent: Counter
    category_popularity: Counter
    declared_tea: frozenset[str]
    survey_age_days: float | None


@dataclass(frozen=True, slots=True)
class Matrix:
    names: tuple[str, ...]
    values: np.ndarray
    components: np.ndarray


def adapt(mode: Mode, rows: tuple[RankingFeatures, ...], context: FeatureContext,
          attributes: tuple[str, ...], categories: tuple[str, ...]) -> Matrix:
    observed, retained, retained_tea = Counter(), Counter(), Counter()
    seen = set()
    new_category_orders = disagreement_lines = 0
    for order in context.orders:
        order_categories = {context.categories_by_product[pid] for pid in order}
        new_category_orders += bool(order_categories - seen)
        seen.update(order_categories)
        observed.update(context.categories_by_product[pid] for pid in order)
        if context.declared_tea:
            disagreement_lines += sum(context.tea_type_by_product[pid] not in context.declared_tea for pid in order)

    for pid, count in context.history.product_counts.items():
        retained[context.categories_by_product[pid]] += count
        tags = context.approved_tags.get(pid, ())
        for tag in tags:
            retained_tea[tag] += count / len(tags)

    observed_total, retained_total = observed.total(), retained.total()
    measured_total = retained_tea.total()
    disagreement = disagreement_lines / observed_total if context.declared_tea else 0.
    counts = [context.popularity[r.product_id] for r in rows]
    low, high = min(counts), max(counts)
    max_frequency = max(r.frequency for r in rows)
    max_recency = max(r.recency_days or 0. for r in rows)
    values, components = [], []
    for row, count in zip(rows, counts, strict=True):
        cat = context.categories_by_product[row.product_id]
        affinity = observed[cat] / observed_total
        tags = context.approved_tags.get(row.product_id, ())
        has_implicit = bool(measured_total and tags)
        implicit = sum(retained_tea[tag] for tag in tags) / measured_total if has_implicit else 0.
        observed_taste = [value for group in taste_groups(row) for value in group.values()]
        compatibility = float(np.mean(observed_taste)) if observed_taste else 0.
        common = (
            log1p(count), log1p(context.recent[row.product_id]), log1p(context.category_popularity[cat]),
            affinity, retained[cat] / retained_total, log1p(count) * row.item_cf, row.tfidf * affinity,
        )
        if mode == "EXPLORE":
            extra = (len(context.history.purchased_ids) / observed_total, new_category_orders / len(context.orders))
            personalized = mode_signal = affinity
        else:
            recency, interval = row.recency_days, row.repeat_interval_days
            if recency is None:
                raise ValueError("FAMILIAR_RECENCY_REQUIRED")
            ratio = min(6., recency / interval) if interval else 0.
            mode_signal = exp(-abs(recency - interval) / max(1., interval)) if interval else 0.
            personalized = (row.frequency / max_frequency + (1 - recency / max_recency if max_recency else 1.)) / 2
            extra = (ratio, float(interval is None), mode_signal, personalized,
                     log1p(row.frequency) / (1 + recency / 30.))
        taste = (
            compatibility, float(not observed_taste), log1p(context.survey_age_days or 0.),
            float(context.survey_age_days is None), implicit, float(not has_implicit), disagreement,
            float(not context.declared_tea), compatibility * affinity, compatibility * mode_signal,
        )
        values.append((*vector(row, attributes, categories), *common, *extra, *taste))
        components.append(((count - low) / (high - low) if high > low else 0.,
                           row.hybrid, personalized, compatibility))
    return Matrix(names_for(mode, attributes, categories), np.asarray(values, dtype=np.float32),
                  np.asarray(components, dtype=np.float64))
