import numpy as np

from offline_benchmark.models.base import TrainModel


class ItemItemCF(TrainModel):
    name = "Item-Item CF"

    def __init__(self, training) -> None:
        super().__init__(training)
        matrix = self.interaction_matrix()
        norm = np.sqrt(np.asarray(matrix.power(2).sum(axis=0)).ravel())
        inverse = np.divide(1.0, norm, out=np.zeros_like(norm), where=norm != 0)
        normalized = matrix.multiply(inverse)
        self.similarity = (normalized.T @ normalized).tocsr()
        self.similarity.setdiag(0)
        self.similarity.eliminate_zeros()

    def scores(self, customer_id: str) -> dict[str, float]:
        return self.score_map(np.asarray(self.history_vector(customer_id) @ self.similarity).ravel())
