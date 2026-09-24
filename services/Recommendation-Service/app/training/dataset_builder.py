from collections import defaultdict
import logging
import math

from lightfm.data import Dataset
from sqlalchemy.orm import Session

from app.models.interaction_event import InteractionEventType
from app.repositories.training_data_repository import TrainingDataRepository
from app.training.schemas import (
    ActiveProductRecord,
    AggregatedInteraction,
    TrainingDataSnapshot,
    TrainingDatasetBundle,
    TrainingDatasetMetadata,
)

logger = logging.getLogger(__name__)

PREFERENCE_NEGATIVE_RETURN_REASON = "CHANGE_OF_MIND"
CATEGORY_FEATURE_PREFIX = "category:"
INGREDIENT_FEATURE_PREFIX = "ingredient:"


class EmptyProductUniverseError(ValueError):
    """Raised when no active, non-deleted product exists for training."""


def calculate_interaction_weight(effective_purchase_count: int) -> float:
    if effective_purchase_count <= 0:
        raise ValueError("effective_purchase_count must be greater than zero")
    return 1.0 + math.log1p(effective_purchase_count)


class TrainingDatasetBuilder:
    """Builds the deterministic LightFM dataset contract without training a model."""

    def __init__(
        self,
        repository: TrainingDataRepository | None = None,
    ) -> None:
        self.repository = repository or TrainingDataRepository()

    def build(self, db: Session) -> TrainingDatasetBundle:
        return self.build_from_snapshot(self.repository.load_snapshot(db))

    def build_from_snapshot(
        self,
        snapshot: TrainingDataSnapshot,
    ) -> TrainingDatasetBundle:
        products = self._normalize_products(snapshot.active_products)
        if not products:
            raise EmptyProductUniverseError(
                "Training dataset requires at least one ACTIVE, non-deleted product"
            )

        product_ids = sorted(products)
        product_id_set = set(product_ids)
        ingredients_by_product = self._group_ingredients(
            snapshot=snapshot,
            valid_product_ids=product_id_set,
        )

        (
            aggregated_interactions,
            valid_event_count,
            purchase_event_count,
            preference_negative_return_count,
            orphan_interaction_count,
        ) = self._aggregate_interactions(
            snapshot=snapshot,
            valid_product_ids=product_id_set,
        )

        positive_interactions = tuple(
            interaction
            for interaction in aggregated_interactions
            if interaction.effective_purchase_count > 0
        )
        users = sorted(
            {interaction.customer_id for interaction in positive_interactions}
        )

        category_tokens = sorted(
            {
                self._category_token(product.category_code)
                for product in products.values()
                if self._clean_identifier(product.category_code) is not None
            }
        )
        ingredient_tokens = sorted(
            {
                self._ingredient_token(ingredient_code)
                for ingredient_codes in ingredients_by_product.values()
                for ingredient_code in ingredient_codes
            }
        )
        item_feature_tokens = sorted(category_tokens + ingredient_tokens)

        dataset = Dataset()
        dataset.fit(
            users=users,
            items=product_ids,
            item_features=item_feature_tokens,
        )

        interaction_tuples = [
            (
                interaction.customer_id,
                interaction.product_id,
                interaction.weight,
            )
            for interaction in positive_interactions
        ]
        interactions, interaction_weights = dataset.build_interactions(
            interaction_tuples
        )

        item_feature_records = []
        for product_id in product_ids:
            product = products[product_id]
            feature_tokens: list[str] = []
            category_code = self._clean_identifier(product.category_code)
            if category_code is not None:
                feature_tokens.append(self._category_token(category_code))
            feature_tokens.extend(
                self._ingredient_token(code)
                for code in ingredients_by_product[product_id]
            )
            item_feature_records.append((product_id, sorted(feature_tokens)))

        # Dataset item identity features remain enabled. Only namespaced content
        # features are supplied here, avoiding a redundant manual product:<id> token.
        item_features = dataset.build_item_features(item_feature_records)

        (
            user_id_map,
            user_feature_map,
            item_id_map,
            item_feature_map,
        ) = dataset.mapping()

        positive_product_ids = {
            interaction.product_id for interaction in positive_interactions
        }
        products_missing_category = sum(
            1
            for product in products.values()
            if self._clean_identifier(product.category_code) is None
        )
        products_missing_bom = sum(
            1
            for product_id in product_ids
            if not ingredients_by_product[product_id]
        )
        weights = [interaction.weight for interaction in positive_interactions]
        warnings = self._build_warnings(
            orphan_interaction_count=orphan_interaction_count,
            products_missing_category=products_missing_category,
            products_missing_bom=products_missing_bom,
            positive_pair_count=len(positive_interactions),
        )

        metadata = TrainingDatasetMetadata(
            raw_event_count=snapshot.raw_event_count,
            valid_event_count=valid_event_count,
            purchase_event_count=purchase_event_count,
            preference_negative_return_count=preference_negative_return_count,
            aggregated_pair_count=len(aggregated_interactions),
            positive_pair_count=len(positive_interactions),
            zero_or_negative_pair_count=(
                len(aggregated_interactions) - len(positive_interactions)
            ),
            user_count=len(users),
            active_product_count=len(product_ids),
            interaction_item_count=len(positive_product_ids),
            cold_start_item_count=len(product_id_set - positive_product_ids),
            orphan_interaction_count=orphan_interaction_count,
            products_missing_category=products_missing_category,
            products_missing_bom=products_missing_bom,
            category_feature_count=len(category_tokens),
            ingredient_feature_count=len(ingredient_tokens),
            interaction_matrix_shape=tuple(interactions.shape),
            item_feature_matrix_shape=tuple(item_features.shape),
            interaction_nnz=int(interactions.nnz),
            item_feature_nnz=int(item_features.nnz),
            weight_min=min(weights) if weights else None,
            weight_max=max(weights) if weights else None,
            weight_mean=(sum(weights) / len(weights)) if weights else None,
            warnings=warnings,
        )

        logger.info(
            "Built LightFM dataset: users=%d, items=%d, positive_interactions=%d, "
            "cold_start_items=%d, orphan_interactions=%d, missing_bom=%d, "
            "interaction_shape=%s, item_feature_shape=%s",
            metadata.user_count,
            metadata.active_product_count,
            metadata.positive_pair_count,
            metadata.cold_start_item_count,
            metadata.orphan_interaction_count,
            metadata.products_missing_bom,
            metadata.interaction_matrix_shape,
            metadata.item_feature_matrix_shape,
        )

        return TrainingDatasetBundle(
            dataset=dataset,
            interactions=interactions,
            interaction_weights=interaction_weights,
            item_features=item_features,
            metadata=metadata,
            user_id_map=user_id_map,
            user_feature_map=user_feature_map,
            item_id_map=item_id_map,
            item_feature_map=item_feature_map,
        )

    @classmethod
    def _normalize_products(
        cls,
        records: tuple[ActiveProductRecord, ...],
    ) -> dict[str, ActiveProductRecord]:
        products: dict[str, ActiveProductRecord] = {}
        for record in records:
            product_id = cls._clean_identifier(record.product_id)
            if product_id is None:
                continue
            products[product_id] = ActiveProductRecord(
                product_id=product_id,
                category_code=cls._clean_identifier(record.category_code),
            )
        return products

    @classmethod
    def _group_ingredients(
        cls,
        snapshot: TrainingDataSnapshot,
        valid_product_ids: set[str],
    ) -> dict[str, tuple[str, ...]]:
        grouped: dict[str, set[str]] = {
            product_id: set() for product_id in sorted(valid_product_ids)
        }
        for record in snapshot.active_product_ingredients:
            product_id = cls._clean_identifier(record.product_id)
            ingredient_code = cls._clean_identifier(record.ingredient_code)
            if product_id not in valid_product_ids or ingredient_code is None:
                continue
            grouped[product_id].add(ingredient_code)
        return {
            product_id: tuple(sorted(ingredient_codes))
            for product_id, ingredient_codes in sorted(grouped.items())
        }

    @classmethod
    def _aggregate_interactions(
        cls,
        snapshot: TrainingDataSnapshot,
        valid_product_ids: set[str],
    ) -> tuple[tuple[AggregatedInteraction, ...], int, int, int, int]:
        pair_counts: dict[tuple[str, str], list[int]] = defaultdict(
            lambda: [0, 0]
        )
        valid_event_count = 0
        purchase_event_count = 0
        preference_negative_return_count = 0
        orphan_interaction_count = 0

        for event in snapshot.interaction_events:
            customer_id = cls._clean_identifier(event.customer_id)
            product_id = cls._clean_identifier(event.product_id)
            event_type = cls._normalize_contract_value(event.event_type)
            return_reason = cls._normalize_contract_value(event.return_reason)

            if (
                customer_id is None
                or product_id is None
                or event_type
                not in {
                    InteractionEventType.PURCHASE.value,
                    InteractionEventType.RETURN.value,
                }
            ):
                continue

            valid_event_count += 1
            is_purchase = event_type == InteractionEventType.PURCHASE.value
            is_preference_negative_return = (
                event_type == InteractionEventType.RETURN.value
                and return_reason == PREFERENCE_NEGATIVE_RETURN_REASON
            )

            if is_purchase:
                purchase_event_count += 1
            elif is_preference_negative_return:
                preference_negative_return_count += 1

            if product_id not in valid_product_ids:
                orphan_interaction_count += 1
                continue

            if is_purchase:
                pair_counts[(customer_id, product_id)][0] += 1
            elif is_preference_negative_return:
                pair_counts[(customer_id, product_id)][1] += 1

        aggregated: list[AggregatedInteraction] = []
        for customer_id, product_id in sorted(pair_counts):
            purchase_count, return_count = pair_counts[(customer_id, product_id)]
            effective_purchase_count = max(purchase_count - return_count, 0)
            weight = (
                calculate_interaction_weight(effective_purchase_count)
                if effective_purchase_count > 0
                else 0.0
            )
            aggregated.append(
                AggregatedInteraction(
                    customer_id=customer_id,
                    product_id=product_id,
                    purchase_count=purchase_count,
                    return_count=return_count,
                    effective_purchase_count=effective_purchase_count,
                    weight=weight,
                )
            )

        return (
            tuple(aggregated),
            valid_event_count,
            purchase_event_count,
            preference_negative_return_count,
            orphan_interaction_count,
        )

    @staticmethod
    def _clean_identifier(value: str | None) -> str | None:
        if value is None:
            return None
        normalized = str(value).strip()
        return normalized or None

    @classmethod
    def _normalize_contract_value(cls, value: str | None) -> str | None:
        normalized = cls._clean_identifier(value)
        return normalized.upper() if normalized is not None else None

    @staticmethod
    def _category_token(category_code: str) -> str:
        return f"{CATEGORY_FEATURE_PREFIX}{category_code}"

    @staticmethod
    def _ingredient_token(ingredient_code: str) -> str:
        return f"{INGREDIENT_FEATURE_PREFIX}{ingredient_code}"

    @staticmethod
    def _build_warnings(
        orphan_interaction_count: int,
        products_missing_category: int,
        products_missing_bom: int,
        positive_pair_count: int,
    ) -> tuple[str, ...]:
        warnings: list[str] = []
        if orphan_interaction_count:
            warnings.append(
                f"Excluded {orphan_interaction_count} orphan interaction event(s)"
            )
        if products_missing_category:
            warnings.append(
                f"Found {products_missing_category} active product(s) without category"
            )
        if products_missing_bom:
            warnings.append(
                f"Found {products_missing_bom} active product(s) without active BOM"
            )
        if positive_pair_count == 0:
            warnings.append("No retained positive interactions")
        return tuple(warnings)
