from collections.abc import Mapping
import math


def top_five(scores: Mapping[str, float]) -> tuple[str, ...]:
    if any(not math.isfinite(float(score)) for score in scores.values()):
        raise ValueError("Cannot rank non-finite model scores")
    return tuple(sorted(scores, key=lambda product: (-scores[product], product))[:5])


def matching_hits(top5: tuple[str, ...], test: tuple[str, ...]) -> tuple[str, ...]:
    actual = set(test)
    return tuple(product for product in top5 if product in actual)
