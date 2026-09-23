from dataclasses import dataclass

from lightfm.data import Dataset
from scipy.sparse import coo_matrix, csr_matrix


@dataclass(frozen=True, slots=True)
class TrainingInteractionEvent:
    customer_id: str | None
    product_id: str | None
    event_type: str | None
    return_reason: str | None


@dataclass(frozen=True, slots=True)
class ActiveProductRecord:
    product_id: str
    category_code: str | None


@dataclass(frozen=True, slots=True)
class ActiveProductIngredientRecord:
    product_id: str
    ingredient_code: str | None


@dataclass(frozen=True, slots=True)
class TrainingDataSnapshot:
    raw_event_count: int
    interaction_events: tuple[TrainingInteractionEvent, ...]
    active_products: tuple[ActiveProductRecord, ...]
    active_product_ingredients: tuple[ActiveProductIngredientRecord, ...]


@dataclass(frozen=True, slots=True)
class AggregatedInteraction:
    customer_id: str
    product_id: str
    purchase_count: int
    return_count: int
    effective_purchase_count: int
    weight: float


@dataclass(frozen=True, slots=True)
class TrainingDatasetMetadata:
    raw_event_count: int
    valid_event_count: int
    purchase_event_count: int
    preference_negative_return_count: int
    aggregated_pair_count: int
    positive_pair_count: int
    zero_or_negative_pair_count: int
    user_count: int
    active_product_count: int
    interaction_item_count: int
    cold_start_item_count: int
    orphan_interaction_count: int
    products_missing_category: int
    products_missing_bom: int
    category_feature_count: int
    ingredient_feature_count: int
    interaction_matrix_shape: tuple[int, int]
    item_feature_matrix_shape: tuple[int, int]
    interaction_nnz: int
    item_feature_nnz: int
    weight_min: float | None
    weight_max: float | None
    weight_mean: float | None
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class TrainingDatasetBundle:
    dataset: Dataset
    interactions: coo_matrix
    interaction_weights: coo_matrix
    item_features: csr_matrix
    metadata: TrainingDatasetMetadata
    user_id_map: dict[str, int]
    user_feature_map: dict[str, int]
    item_id_map: dict[str, int]
    item_feature_map: dict[str, int]
