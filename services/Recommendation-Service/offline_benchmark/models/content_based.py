import numpy as np

from offline_benchmark.models.base import TrainModel


class ContentBased(TrainModel):
    name = "Content-Based"

    def __init__(self, training) -> None:
        super().__init__(training)
        features = {}
        for product in training.products:
            tokens = {f"ingredient:{code}" for code in product.ingredient_codes}
            if product.category_code:
                tokens.add(f"category:{product.category_code}")
            features[product.product_id] = tokens
        vocabulary = {token: i for i, token in enumerate(sorted(set().union(*features.values())))}
        self.features = np.zeros((len(self.product_ids), len(vocabulary)))
        for product, tokens in features.items():
            for token in tokens:
                self.features[self.product_index[product], vocabulary[token]] = 1.0
        norms = np.linalg.norm(self.features, axis=1, keepdims=True)
        self.features = np.divide(self.features, norms, out=np.zeros_like(self.features), where=norms != 0)

    def profile_scores(self, history: np.ndarray) -> dict[str, float]:
        profile = history @ self.features
        norm = np.linalg.norm(profile)
        if norm:
            profile = profile / norm
        return self.score_map(self.features @ profile)

    def scores(self, customer_id: str) -> dict[str, float]:
        return self.profile_scores(self.history_vector(customer_id))
