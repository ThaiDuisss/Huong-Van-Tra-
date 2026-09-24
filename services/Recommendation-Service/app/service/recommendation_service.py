from dataclasses import dataclass
import logging

import numpy as np
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.customer_interaction import CustomerInteraction
from app.models.interaction_event import InteractionEvent, InteractionEventType
from app.models.product import Product
from app.schemas.recommendation import (
    RecommendationItem,
    RecommendationResponse,
)
from app.training.model_artifact import ModelArtifactError, ModelArtifactStore


logger = logging.getLogger(__name__)
ACTIVE_PRODUCT_STATUS = "ACTIVE"


@dataclass(frozen=True, slots=True)
class ProductView:
    product_id: str
    product_code: str
    name: str


class RecommendationService:
    def __init__(self, artifact_store: ModelArtifactStore) -> None:
        self.artifact_store = artifact_store

    def recommend(
        self,
        db: Session,
        customer_id: str,
        limit: int,
    ) -> RecommendationResponse:
        try:
            artifact = self.artifact_store.load()
        except ModelArtifactError as exc:
            logger.warning("Using recommendation fallback: %s", exc)
            return self._fallback(
                db,
                customer_id=customer_id,
                limit=limit,
                reason="MODEL_NOT_AVAILABLE",
            )

        user_index = artifact.user_id_map.get(customer_id)
        if user_index is None:
            return self._fallback(
                db,
                customer_id=customer_id,
                limit=limit,
                reason="CUSTOMER_NOT_IN_MODEL",
            )

        products = self._load_active_products(db)
        purchased_product_ids = self._load_purchased_product_ids(db, customer_id)
        reverse_item_map = {
            internal_id: product_id
            for product_id, internal_id in artifact.item_id_map.items()
        }
        candidate_internal_ids = np.array(
            [
                internal_id
                for internal_id in range(len(reverse_item_map))
                if reverse_item_map[internal_id] in products
                and reverse_item_map[internal_id] not in purchased_product_ids
            ],
            dtype=np.int32,
        )

        if candidate_internal_ids.size == 0:
            return RecommendationResponse(
                customer_id=customer_id,
                source="MODEL",
                model_type=artifact.model_type,
                model_contract_version=artifact.contract_version,
                trained_at=artifact.trained_at,
                items=[],
            )

        scores = artifact.model.predict(
            np.full(candidate_internal_ids.shape, user_index, dtype=np.int32),
            candidate_internal_ids,
            item_features=artifact.item_features,
            num_threads=1,
        )
        ranked = sorted(
            zip(candidate_internal_ids.tolist(), scores.tolist()),
            key=lambda pair: (-pair[1], reverse_item_map[pair[0]]),
        )[:limit]
        items = [
            self._to_response_item(
                rank=rank,
                product=products[reverse_item_map[internal_id]],
                score=score,
            )
            for rank, (internal_id, score) in enumerate(ranked, start=1)
        ]
        return RecommendationResponse(
            customer_id=customer_id,
            source="MODEL",
            model_type=artifact.model_type,
            model_contract_version=artifact.contract_version,
            trained_at=artifact.trained_at,
            items=items,
        )

    def _fallback(
        self,
        db: Session,
        customer_id: str,
        limit: int,
        reason: str,
    ) -> RecommendationResponse:
        purchased_product_ids = self._load_purchased_product_ids(db, customer_id)
        popularity = func.coalesce(
            func.sum(CustomerInteraction.total_purchase_count),
            0,
        ).label("popularity")
        stmt = (
            select(Product.id, Product.product_code, Product.name, popularity)
            .outerjoin(
                CustomerInteraction,
                CustomerInteraction.product_id == Product.id,
            )
            .where(
                Product.status == ACTIVE_PRODUCT_STATUS,
                Product.is_deleted.is_(False),
                Product.id.not_in(purchased_product_ids),
            )
            .group_by(Product.id, Product.product_code, Product.name)
            .order_by(popularity.desc(), Product.id.asc())
            .limit(limit)
        )
        items = [
            self._to_response_item(
                rank=rank,
                product=ProductView(
                    product_id=row.id,
                    product_code=row.product_code,
                    name=row.name,
                ),
                score=float(row.popularity),
            )
            for rank, row in enumerate(db.execute(stmt), start=1)
        ]
        return RecommendationResponse(
            customer_id=customer_id,
            source="FALLBACK",
            fallback_reason=reason,
            items=items,
        )

    @staticmethod
    def _load_active_products(db: Session) -> dict[str, ProductView]:
        stmt = (
            select(Product.id, Product.product_code, Product.name)
            .where(
                Product.status == ACTIVE_PRODUCT_STATUS,
                Product.is_deleted.is_(False),
            )
            .order_by(Product.id.asc())
        )
        return {
            row.id: ProductView(
                product_id=row.id,
                product_code=row.product_code,
                name=row.name,
            )
            for row in db.execute(stmt)
        }

    @staticmethod
    def _load_purchased_product_ids(db: Session, customer_id: str) -> set[str]:
        stmt = select(InteractionEvent.product_id).where(
            InteractionEvent.customer_id == customer_id,
            InteractionEvent.event_type == InteractionEventType.PURCHASE.value,
        )
        return set(db.scalars(stmt))

    @staticmethod
    def _to_response_item(
        rank: int,
        product: ProductView,
        score: float,
    ) -> RecommendationItem:
        return RecommendationItem(
            rank=rank,
            product_id=product.product_id,
            product_code=product.product_code,
            name=product.name,
            score=score,
        )
