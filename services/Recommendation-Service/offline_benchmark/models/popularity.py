from offline_benchmark.models.base import TrainModel


class Popularity(TrainModel):
    name = "Popularity"

    def scores(self, customer_id: str) -> dict[str, float]:
        return {product: float(self.counts[product]) for product in self.product_ids}
