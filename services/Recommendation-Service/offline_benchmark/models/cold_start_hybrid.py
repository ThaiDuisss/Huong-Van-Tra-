import numpy as np

from offline_benchmark.models.base import blend
from offline_benchmark.models.content_based import ContentBased
from offline_benchmark.models.popularity import Popularity


class ColdStartHybrid:
    """No target-user profile: blend global TRAIN popularity and content prior."""

    name = "Cold-Start Hybrid"

    def __init__(self, training) -> None:
        popularity = Popularity(training)
        content = ContentBased(training)
        history = np.array([popularity.counts[p] for p in content.product_ids], dtype=float)
        self.ranking = blend(popularity.scores(""), content.profile_scores(history))

    def scores(self, customer_id: str) -> dict[str, float]:
        return dict(self.ranking)
