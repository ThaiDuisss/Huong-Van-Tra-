from dataclasses import dataclass
from math import fsum, isfinite, log2


K = 5


@dataclass(frozen=True, slots=True)
class UserMetrics:
    precision_at_5: float
    recall_at_5: float
    ndcg_at_5: float
    ap_at_5: float


def ranking_metrics(top5: tuple[str, ...], test: tuple[str, ...]) -> UserMetrics:
    """Binary product relevance; empty TEST is an explicit validation error."""
    if len(top5) > K or len(set(top5)) != len(top5):
        raise ValueError("Top-5 must contain at most five unique product IDs")
    if any(not isinstance(product, str) or not product.strip() for product in top5 + test):
        raise ValueError("Recommendations and TEST must contain nonblank product IDs")
    relevant = set(test)
    if not relevant:
        raise ValueError("No relevant TEST products: Recall, IDCG, and AP are undefined")

    hits = 0
    gains, precisions = [], []
    for rank, product in enumerate(top5, start=1):
        if product in relevant:
            hits += 1
            gains.append(1.0 / log2(rank + 1))
            precisions.append(hits / rank)
    ideal_count = min(len(relevant), K)
    ideal_gain = fsum(1.0 / log2(rank + 1) for rank in range(1, ideal_count + 1))
    result = UserMetrics(
        precision_at_5=hits / K,
        recall_at_5=hits / len(relevant),
        ndcg_at_5=fsum(gains) / ideal_gain,
        ap_at_5=fsum(precisions) / ideal_count,
    )
    for value in (result.precision_at_5, result.recall_at_5, result.ndcg_at_5, result.ap_at_5):
        if not isfinite(value) or not 0.0 <= value <= 1.0:
            raise ValueError("Invalid ranking metric: expected finite value between zero and one")
    return result
