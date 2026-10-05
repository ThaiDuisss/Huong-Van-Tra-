from collections import Counter

import numpy as np
from scipy.sparse import csr_matrix

from offline_benchmark.common.types import TrainingInput


class TrainModel:
    def __init__(self, training: TrainingInput) -> None:
        self.training = training
        self.product_ids = tuple(sorted(product.product_id for product in training.products))
        self.product_index = {product: i for i, product in enumerate(self.product_ids)}
        self.user_index = {user: i for i, user in enumerate(training.customer_ids)}
        self.counts = Counter(event.product_id for event in training.purchases)
        self.user_counts = {user: Counter() for user in training.customer_ids}
        for event in training.purchases:
            if event.customer_id not in self.user_counts:
                raise ValueError("TRAIN contains a customer outside the eligible population")
            self.user_counts[event.customer_id][event.product_id] += 1

    def history_vector(self, customer_id: str) -> np.ndarray:
        counts = self.user_counts.get(customer_id, {})
        return np.array([counts.get(product, 0) for product in self.product_ids], dtype=float)

    def interaction_matrix(self) -> csr_matrix:
        coordinates = [
            (self.user_index[user], self.product_index[product], count)
            for user, counts in self.user_counts.items()
            for product, count in counts.items() if product in self.product_index
        ]
        rows, columns, values = zip(*coordinates) if coordinates else ((), (), ())
        return csr_matrix((values, (rows, columns)),
                          shape=(len(self.user_index), len(self.product_ids)), dtype=float)

    def score_map(self, scores: np.ndarray) -> dict[str, float]:
        return dict(zip(self.product_ids, map(float, scores)))


def normalize_scores(scores: dict[str, float]) -> dict[str, float]:
    values = list(scores.values())
    if not values:
        return {}
    low, high = min(values), max(values)
    return {product: (score - low) / (high - low) if high > low else 0.0
            for product, score in scores.items()}


def blend(first: dict[str, float], second: dict[str, float]) -> dict[str, float]:
    left, right = normalize_scores(first), normalize_scores(second)
    return {product: 0.5 * left[product] + 0.5 * right[product] for product in left}
