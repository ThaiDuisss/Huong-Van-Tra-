from collections.abc import Mapping
from math import isclose, isfinite

import numpy as np
from scipy.sparse.linalg import svds

from offline_benchmark.models.base import TrainModel


SVD_RANDOM_SEED = 42
SVD_SCORE_ATOL = 1e-12
SVD_SCORE_RTOL = 1e-12


def stable_svd_scores(scores: Mapping[str, float]) -> dict[str, float]:
    """Give SVD near-ties one score so the existing product-ID tie key applies.

    Groups are anchored to their highest raw score, avoiding nontransitive
    pairwise-close sorting and chains that collapse genuinely different scores.
    The tolerance is max(1e-12 absolute, 1e-12 relative to either score).
    """
    if any(not isfinite(float(score)) for score in scores.values()):
        raise ValueError("Cannot rank non-finite SVD scores")
    ranked = sorted(scores, key=lambda product: (-scores[product], product))
    result = {}
    anchor = None
    for product in ranked:
        score = float(scores[product])
        if anchor is None or not isclose(score, anchor, rel_tol=SVD_SCORE_RTOL, abs_tol=SVD_SCORE_ATOL):
            anchor = score
        result[product] = anchor
    return result


class SVD(TrainModel):
    """Truncated SVD of the TRAIN purchase-count matrix (no rating imputation)."""

    name = "SVD"

    def __init__(self, training) -> None:
        super().__init__(training)
        matrix = self.interaction_matrix()
        dimension = min(matrix.shape)
        if matrix.nnz == 0:
            self.user_factors = np.zeros((matrix.shape[0], 1))
            self.item_factors = np.zeros((1, matrix.shape[1]))
        elif dimension <= 1:
            left, singular, right = np.linalg.svd(matrix.toarray(), full_matrices=False)
            self.user_factors, self.item_factors = left * singular, right
        else:
            rank = min(32, dimension - 1)
            # A constant start is symmetric on the degenerate fixture and can
            # trigger ARPACK breakdown/restarts. Use a reproducible nonuniform
            # unit vector from a fresh local RNG, independent of global state.
            generator = np.random.default_rng(SVD_RANDOM_SEED)
            initial = generator.uniform(0.5, 1.5, size=dimension)
            initial /= np.linalg.norm(initial)
            left, singular, right = svds(
                matrix, k=rank, solver="arpack", which="LM", tol=0,
                v0=initial, random_state=SVD_RANDOM_SEED,
            )
            self.user_factors, self.item_factors = left * singular, right

    def scores(self, customer_id: str) -> dict[str, float]:
        user = self.user_index[customer_id]
        raw = self.score_map(self.user_factors[user] @ self.item_factors)
        return stable_svd_scores(raw)
