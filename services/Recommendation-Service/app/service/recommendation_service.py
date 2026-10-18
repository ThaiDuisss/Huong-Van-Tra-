import logging

from sqlalchemy.orm import Session

from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_model import (
    CONTRACT_VERSION, MODEL_TYPE, V1ModelError, catalog_fingerprint, hybrid_scores,
)
from app.repositories.v1_recommendation_repository import CustomerPurchaseHistory, V1RecommendationRepository
from app.schemas.recommendation import RecommendationItem, RecommendationMode, RecommendationResponse


logger = logging.getLogger(__name__)
# Approved interim business policy; not a statistically optimal threshold.
MIN_HYBRID_PURCHASE_ORDERS = 2


class RecommendationService:
    def __init__(self, artifact_store: V1ArtifactStore,
                 repository: V1RecommendationRepository | None = None) -> None:
        self.artifact_store = artifact_store
        self.repository = repository or V1RecommendationRepository()

    def recommend(self, db: Session, customer_id: str, limit: int,
                  exclude_purchased: bool = True,
                  mode: RecommendationMode | None = None) -> RecommendationResponse:
        history = self.repository.customer_history(db, customer_id)
        products, eligible = None, None
        if mode is not None:
            limit = min(limit, 5)
            exclude_purchased = mode == RecommendationMode.DISCOVERY
            products = self.repository.active_catalog(db)
            catalog = {p.product_id for p in products}
            bought = set(history.product_counts)
            eligible = catalog - bought if exclude_purchased else catalog & bought
        purchased = set(history.product_counts) if exclude_purchased else set()
        if history.missing_order_events:
            logger.warning("Ignored %d PURCHASE events without real order IDs for routing",
                           history.missing_order_events)
        if history.distinct_order_count < MIN_HYBRID_PURCHASE_ORDERS:
            return self._popularity(db, customer_id, limit, purchased, history, exclude_purchased,
                                    mode=mode, eligible=eligible)

        # Catalog queries stay outside model error handling: never swallow DB errors.
        if products is None:
            products = self.repository.active_catalog(db)
        try:
            artifact = self.artifact_store.load()
            if artifact.catalog_sha256 != catalog_fingerprint(products):
                raise V1ModelError("Current catalog differs from artifact; refresh V1 snapshot")
            candidates = tuple(p.product_id for p in products
                               if (p.product_id in eligible if eligible is not None else p.product_id not in purchased))
            content, cf = artifact.components(history.product_counts)
            scores, both_flat = hybrid_scores(content, cf, candidates)
            if candidates and both_flat:
                return self._popularity(db, customer_id, limit, purchased, history,
                                        exclude_purchased, "BOTH_COMPONENTS_FLAT", mode, eligible)
        except V1ModelError as exc:
            logger.warning("Using V1 Popularity fallback: %s", exc)
            return self._popularity(db, customer_id, limit, purchased, history,
                                    exclude_purchased, "V1_MODEL_NOT_AVAILABLE", mode, eligible)
        views = {p.product_id: p for p in products}
        ranked = sorted(scores, key=lambda p: (-scores[p], p))[:limit]
        return RecommendationResponse(
            customer_id=customer_id, source="HYBRID", model_type=MODEL_TYPE,
            model_contract_version=CONTRACT_VERSION, trained_at=artifact.created_at,
            purchase_order_count=history.distinct_order_count, exclude_purchased=exclude_purchased,
            mode=mode,
            items=[RecommendationItem(rank=rank, product_id=p, product_code=views[p].product_code,
                                      name=views[p].name, score=scores[p])
                   for rank, p in enumerate(ranked, 1)],
        )

    def _popularity(self, db: Session, customer_id: str, limit: int, purchased: set[str],
                    history: CustomerPurchaseHistory, exclude_purchased: bool,
                    reason: str | None = None, mode: RecommendationMode | None = None,
                    eligible: set[str] | None = None) -> RecommendationResponse:
        rows = (self.repository.popularity(db, purchased, limit) if eligible is None
                else self.repository.popularity(db, purchased, limit, eligible_ids=eligible))
        return RecommendationResponse(
            customer_id=customer_id, source="FALLBACK" if reason else "POPULARITY",
            fallback_reason=reason, model_type="popularity", model_contract_version=CONTRACT_VERSION,
            purchase_order_count=history.distinct_order_count, exclude_purchased=exclude_purchased,
            mode=mode,
            items=[RecommendationItem(rank=rank, product_id=p, product_code=code, name=name, score=float(score))
                   for rank, (p, code, name, score) in enumerate(rows, 1)],
        )
