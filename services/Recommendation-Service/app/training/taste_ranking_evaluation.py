from collections.abc import Callable
from math import fsum

from app.recommendation.taste_ranking_artifact import LogisticModel
from app.recommendation.taste_ranking_features import FeatureError, Mode
from app.recommendation.taste_rules import EXACT_POLICY_VERSION
from app.training.taste_ranking_dataset import RankingQuery, Sample
from offline_benchmark.metrics.ranking_metrics import K, ranking_metrics


def _top5(samples: tuple[Sample, ...], key: Callable[[Sample], tuple]) -> tuple[str, ...]:
    return tuple(row.features.product_id for row in sorted(samples, key=key)[:K])


def learned_top5(query: RankingQuery, model: LogisticModel) -> tuple[tuple[str, ...], bool]:
    if not query.hybrid_has_signal:
        return (), False
    scored = tuple(row for row in query.samples if row.features.has_signal)
    if query.can_rerank:
        try:
            scores = model.score([row.features for row in scored])
        except (FeatureError, ArithmeticError, ValueError):
            pass
        else:
            ranked = sorted(zip(scored, scores), key=lambda item: (-item[1], item[0].features.product_id))
            return tuple(row.features.product_id for row, _ in ranked[:K]), True
    return _top5(scored, lambda row: (-row.features.hybrid, row.features.product_id)), False


def baseline_top5(query: RankingQuery, baseline: str) -> tuple[str, ...]:
    if baseline == "HYBRID_V1":
        if not query.hybrid_has_signal:
            return ()
        scored = tuple(row for row in query.samples if row.features.has_signal)
        return _top5(scored, lambda row: (-row.features.hybrid, row.features.product_id))
    if baseline == "POPULARITY":
        return _top5(query.samples, lambda row: (-row.popularity, row.features.product_id))
    if baseline == "TF_IDF":
        return _top5(query.samples, lambda row: (-row.features.tfidf, row.features.product_id))
    if baseline == "ITEM_ITEM_CF":
        return _top5(query.samples, lambda row: (-row.features.item_cf, row.features.product_id))
    if baseline == "MOST_FREQUENTLY_PURCHASED":
        return _top5(query.samples, lambda row: (-row.features.frequency, row.features.product_id))
    if baseline == "FREQUENCY_RECENCY" and query.samples:
        highest_frequency = max(row.features.frequency for row in query.samples)
        longest_days = max(row.features.recency_days for row in query.samples)
        return _top5(query.samples, lambda row: (
            -(row.features.frequency / highest_frequency
              + (1 - row.features.recency_days / longest_days if longest_days else 1)) / 2,
            row.features.product_id,
        ))
    if baseline == "RECENTLY_PURCHASED":
        return _top5(query.samples, lambda row: (row.features.recency_days, row.features.product_id))
    if baseline == "FREQUENCY_RECENCY":
        return ()
    raise FeatureError("UNKNOWN_RANKING_BASELINE")


def _ratio(numerator: int, denominator: int) -> float | None:
    return numerator / denominator if denominator else None


def summarize_rankings(queries: tuple[RankingQuery, ...], predictions: list[tuple[str, ...]]) -> dict:
    if len(queries) != len(predictions):
        raise FeatureError("INCOMPLETE_RANKING_COHORT")
    candidate_ids = {row.features.product_id for query in queries for row in query.samples}
    recommended_ids = {product for top5 in predictions for product in top5}
    metrics = []
    for query, top5 in zip(queries, predictions):
        relevant = tuple(row.features.product_id for row in query.samples if row.label)
        if relevant:
            metrics.append(ranking_metrics(top5, relevant))
    return {
        "eligible_queries": len(queries),
        "evaluable_queries": len(metrics),
        "queries_without_positive": len(queries) - len(metrics),
        "nonempty_rankings": sum(bool(top5) for top5 in predictions),
        "average_returned_k": _ratio(sum(map(len, predictions)), len(queries)),
        "positive_rows": sum(row.label for query in queries for row in query.samples),
        "negative_rows": sum(not row.label for query in queries for row in query.samples),
        "ranking_queries": len(metrics),
        "precision_at_5": fsum(row.precision_at_5 for row in metrics) / len(metrics) if metrics else None,
        "recall_at_5": fsum(row.recall_at_5 for row in metrics) / len(metrics) if metrics else None,
        "ndcg_at_5": fsum(row.ndcg_at_5 for row in metrics) / len(metrics) if metrics else None,
        "hit_rate_at_5": _ratio(sum(row.recall_at_5 > 0 for row in metrics), len(metrics)),
        "coverage_at_5": _ratio(len(recommended_ids), len(candidate_ids)),
        "recommendation_coverage": _ratio(sum(bool(top5) for top5 in predictions), len(queries)),
    }


def evaluate(
    mode: Mode, queries: tuple[RankingQuery, ...], model: LogisticModel, *,
    matching_policy_version: str = EXACT_POLICY_VERSION,
) -> dict:
    """Compare rankings over the same eligible customer/cutoff and product candidates."""
    if matching_policy_version != EXACT_POLICY_VERSION:
        raise FeatureError("MATCHING_POLICY_INCOMPATIBLE")
    eligible = tuple(query for query in queries if query.eligible)
    reports = {}
    baselines = ("POPULARITY", "HYBRID_V1")
    if mode == "EXPLORE":
        baselines += ("TF_IDF", "ITEM_ITEM_CF")
    else:
        baselines += ("MOST_FREQUENTLY_PURCHASED", "RECENTLY_PURCHASED", "FREQUENCY_RECENCY")
    for name, cohort in (
        ("ALL", eligible),
        ("WITH_TASTE", tuple(query for query in eligible if query.has_taste)),
        ("WITHOUT_TASTE", tuple(query for query in eligible if not query.has_taste)),
    ):
        rankings = {baseline: summarize_rankings(cohort, [baseline_top5(query, baseline) for query in cohort])
                    for baseline in baselines}
        predictions = [learned_top5(query, model) for query in cohort]
        rankings["HYBRID_V1_TASTE_RERANKER"] = summarize_rankings(cohort, [top5 for top5, _ in predictions])
        reports[name] = {
            "customers": len({query.customer_id for query in cohort}),
            "eligible_queries": len(cohort),
            "queries_with_candidates": sum(bool(query.samples) for query in cohort),
            "candidate_rows": sum(len(query.samples) for query in cohort),
            "positive_rows": sum(row.label for query in cohort for row in query.samples),
            "taste_feature_candidate_coverage": _ratio(
                sum(row.features.has_taste for query in cohort for row in query.samples),
                sum(len(query.samples) for query in cohort),
            ),
            "reranked_queries": sum(applied for _, applied in predictions),
            "incompatible_preparation_rows": sum(
                row.features.needs_aggregation for query in cohort for row in query.samples
            ),
            "rankings": rankings,
        }
    eligible_customers = len({query.customer_id for query in eligible})
    customers = len({query.customer_id for query in queries})
    return {
        "matching_policy_version": matching_policy_version,
        "customer_queries": len(queries),
        "eligible_customer_coverage": _ratio(eligible_customers, customers),
        "eligible_query_coverage": _ratio(len(eligible), len(queries)),
        "taste_feature_coverage": _ratio(reports["WITH_TASTE"]["eligible_queries"], len(eligible)),
        "cohorts": reports,
    }
