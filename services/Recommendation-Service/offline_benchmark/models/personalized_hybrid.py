from offline_benchmark.models.base import blend
from offline_benchmark.models.content_based import ContentBased
from offline_benchmark.models.item_item_cf import ItemItemCF


class PersonalizedHybrid:
    name = "Personalized Hybrid"

    def __init__(self, training) -> None:
        self.content = ContentBased(training)
        self.collaborative = ItemItemCF(training)

    def scores(self, customer_id: str) -> dict[str, float]:
        return blend(self.content.scores(customer_id), self.collaborative.scores(customer_id))
