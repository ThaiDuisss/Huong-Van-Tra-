from dataclasses import dataclass
from datetime import datetime, timezone
import logging
from time import perf_counter

from lightfm import LightFM
import numpy as np

from app.training.schemas import TrainingDatasetBundle
from app.training.training_config import (
    MODEL_TYPE,
    TRAINING_CONTRACT_VERSION,
    TrainingConfig,
)

logger = logging.getLogger(__name__)

SANITY_PREDICTION_LIMIT = 32


class TrainingValidationError(ValueError):
    """Raised when a dataset bundle is not safe to pass to LightFM.fit."""


class TrainingNumericalError(RuntimeError):
    """Raised when training produces a non-finite representation or prediction."""


@dataclass(frozen=True, slots=True)
class TrainingMetadata:
    model_type: str
    contract_version: str
    started_at: datetime
    completed_at: datetime
    duration_seconds: float
    epochs: int
    loss: str
    components: int
    learning_rate: float
    random_seed: int
    num_threads: int
    user_count: int
    item_count: int
    interaction_count: int
    cold_start_item_count: int
    interaction_matrix_shape: tuple[int, int]
    item_feature_matrix_shape: tuple[int, int]
    sanity_prediction_count: int


@dataclass(frozen=True, slots=True)
class TrainingResult:
    model: LightFM
    dataset_bundle: TrainingDatasetBundle
    config: TrainingConfig
    metadata: TrainingMetadata


class LightFMTrainer:
    """Trains the locked LightFM WARP V1 model from a prepared dataset bundle."""

    def __init__(self, config: TrainingConfig | None = None) -> None:
        self.config = config or TrainingConfig()

    def train(self, bundle: TrainingDatasetBundle) -> TrainingResult:
        self._validate_bundle(bundle)

        metadata = bundle.metadata
        config = self.config
        logger.info(
            "Starting LightFM training: users=%d, items=%d, interactions=%d, "
            "cold_start_items=%d, epochs=%d, components=%d",
            metadata.user_count,
            metadata.active_product_count,
            metadata.interaction_nnz,
            metadata.cold_start_item_count,
            config.epochs,
            config.no_components,
        )

        started_at = datetime.now(timezone.utc)
        started_timer = perf_counter()
        model = LightFM(
            loss=config.loss,
            no_components=config.no_components,
            learning_rate=config.learning_rate,
            random_state=config.random_seed,
        )
        model.fit(
            bundle.interactions,
            sample_weight=bundle.interaction_weights,
            item_features=bundle.item_features,
            epochs=config.epochs,
            num_threads=config.num_threads,
        )
        sanity_prediction_count = self._verify_numerical_sanity(model, bundle)
        duration_seconds = perf_counter() - started_timer
        completed_at = datetime.now(timezone.utc)

        training_metadata = TrainingMetadata(
            model_type=MODEL_TYPE,
            contract_version=TRAINING_CONTRACT_VERSION,
            started_at=started_at,
            completed_at=completed_at,
            duration_seconds=duration_seconds,
            epochs=config.epochs,
            loss=config.loss,
            components=config.no_components,
            learning_rate=config.learning_rate,
            random_seed=config.random_seed,
            num_threads=config.num_threads,
            user_count=metadata.user_count,
            item_count=metadata.active_product_count,
            interaction_count=metadata.interaction_nnz,
            cold_start_item_count=metadata.cold_start_item_count,
            interaction_matrix_shape=metadata.interaction_matrix_shape,
            item_feature_matrix_shape=metadata.item_feature_matrix_shape,
            sanity_prediction_count=sanity_prediction_count,
        )

        logger.info(
            "Completed LightFM training: duration_seconds=%.3f, "
            "sanity_predictions=%d",
            duration_seconds,
            sanity_prediction_count,
        )
        return TrainingResult(
            model=model,
            dataset_bundle=bundle,
            config=config,
            metadata=training_metadata,
        )

    @staticmethod
    def _validate_bundle(bundle: TrainingDatasetBundle) -> None:
        metadata = bundle.metadata
        user_count = len(bundle.user_id_map)
        item_count = len(bundle.item_id_map)
        expected_interaction_shape = (user_count, item_count)

        if user_count == 0:
            raise TrainingValidationError(
                "LightFM training requires at least one retained user"
            )
        if item_count == 0:
            raise TrainingValidationError(
                "LightFM training requires at least one active product"
            )
        if bundle.interactions.nnz == 0:
            raise TrainingValidationError(
                "LightFM training requires at least one positive interaction"
            )
        if bundle.interactions.shape != expected_interaction_shape:
            raise TrainingValidationError(
                "Interaction matrix shape must match the user and item mappings"
            )
        if bundle.interaction_weights.shape != bundle.interactions.shape:
            raise TrainingValidationError(
                "Interaction weights shape must match interactions shape"
            )
        if bundle.item_features.shape[0] != item_count:
            raise TrainingValidationError(
                "Item feature rows must match the active item count"
            )
        if metadata.interaction_matrix_shape != tuple(bundle.interactions.shape):
            raise TrainingValidationError(
                "Dataset metadata interaction shape does not match the matrix"
            )
        if metadata.item_feature_matrix_shape != tuple(bundle.item_features.shape):
            raise TrainingValidationError(
                "Dataset metadata item feature shape does not match the matrix"
            )
        if metadata.interaction_nnz != bundle.interactions.nnz:
            raise TrainingValidationError(
                "Dataset metadata interaction count does not match the matrix"
            )
        if metadata.item_feature_nnz != bundle.item_features.nnz:
            raise TrainingValidationError(
                "Dataset metadata item feature count does not match the matrix"
            )
        if metadata.user_count != user_count:
            raise TrainingValidationError(
                "Dataset metadata user count does not match the user mapping"
            )
        if metadata.active_product_count != item_count:
            raise TrainingValidationError(
                "Dataset metadata item count does not match the item mapping"
            )

        interactions = bundle.interactions.tocsr(copy=True)
        weights = bundle.interaction_weights.tocsr(copy=True)
        interactions.eliminate_zeros()
        weights.eliminate_zeros()
        if interactions.nnz != weights.nnz:
            raise TrainingValidationError(
                "Every positive interaction must have exactly one sample weight"
            )

        interaction_presence = interactions.copy()
        interaction_presence.data = np.ones(
            interaction_presence.nnz,
            dtype=np.int8,
        )
        weight_presence = weights.copy()
        weight_presence.data = np.ones(weight_presence.nnz, dtype=np.int8)
        if (interaction_presence != weight_presence).nnz:
            raise TrainingValidationError(
                "Sample weight coordinates must match interaction coordinates"
            )
        if not np.all(np.isfinite(interactions.data)) or np.any(
            interactions.data <= 0
        ):
            raise TrainingValidationError(
                "Interactions must contain finite positive values only"
            )
        if not np.all(np.isfinite(weights.data)) or np.any(weights.data <= 0):
            raise TrainingValidationError(
                "Sample weights must contain finite positive values only"
            )
        if not np.all(np.isfinite(bundle.item_features.data)):
            raise TrainingValidationError("Item features must contain finite values")

    def _verify_numerical_sanity(
        self,
        model: LightFM,
        bundle: TrainingDatasetBundle,
    ) -> int:
        learned_arrays = (
            model.user_embeddings,
            model.user_biases,
            model.item_embeddings,
            model.item_biases,
        )
        if any(
            array is None or not np.all(np.isfinite(array))
            for array in learned_arrays
        ):
            raise TrainingNumericalError(
                "LightFM training produced NaN or Inf learned parameters"
            )

        prediction_count = min(
            len(bundle.item_id_map),
            SANITY_PREDICTION_LIMIT,
        )
        item_ids = np.arange(prediction_count, dtype=np.int32)
        user_ids = np.zeros(prediction_count, dtype=np.int32)
        predictions = model.predict(
            user_ids,
            item_ids,
            item_features=bundle.item_features,
            num_threads=self.config.num_threads,
        )
        if not np.all(np.isfinite(predictions)):
            raise TrainingNumericalError(
                "LightFM training produced NaN or Inf sanity predictions"
            )
        return prediction_count
