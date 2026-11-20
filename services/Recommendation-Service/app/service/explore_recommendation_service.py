from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, TypeVar

from sqlalchemy.orm import Session

from app.recommendation.v1_artifact import V1ArtifactStore
from app.recommendation.v1_model import CONTRACT_VERSION, V1ModelError, catalog_fingerprint, hybrid_scores
from app.repositories.explore_history_repository import ExploreHistory, ExploreHistoryRepository
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.schemas.explore_recommendation import (
    ExploreProducts, ExploreRecommendationResponse, FamiliarRecommendationResponse,
    PersonalizedRecommendationResponse,
)
from app.schemas.recommendation import RecommendationItem
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch

MIN_EXPLORE_VALID_ORDERS = 5
ResponseT = TypeVar("ResponseT", bound=PersonalizedRecommendationResponse)


@dataclass(frozen=True)
class BehavioralCandidate:
    product_id: str
    score: float
    has_signal: bool


@dataclass(frozen=True)
class CandidateFeatures:
    behavioral: tuple[BehavioralCandidate, ...]
    taste: TasteFeatureBatch


def rank_by_behavior(features: CandidateFeatures, limit: int) -> list[BehavioralCandidate]:
    return sorted((candidate for candidate in features.behavioral if candidate.has_signal),
                  key=lambda candidate: (-candidate.score, candidate.product_id))[:limit]


class PersonalizedRecommendationService:
    def __init__(self, artifact_store: V1ArtifactStore,
                 history_repository: ExploreHistoryRepository | None = None,
                 catalog_repository: V1RecommendationRepository | None = None, *,
                 taste_features: PersonalizedTasteFeatures | None = None,
                 ranker: Callable[[CandidateFeatures, int], list[BehavioralCandidate]] = rank_by_behavior) -> None:
        self.artifact_store = artifact_store
        self.history_repository = history_repository or ExploreHistoryRepository()
        self.catalog_repository = catalog_repository or V1RecommendationRepository()
        self.taste_features = taste_features
        self.ranker = ranker

    def _recommend(self, db: Session, customer_id: str, limit: int,
                   response_model: type[ResponseT], *, familiar: bool) -> ResponseT:
        as_of = datetime.now(timezone.utc)
        history = self.history_repository.history(db, customer_id, as_of.replace(tzinfo=None))
        if history.valid_order_count < MIN_EXPLORE_VALID_ORDERS:
            return response_model(
                customer_id=customer_id, status="INSUFFICIENT_VALID_ORDERS",
                valid_order_count=history.valid_order_count,
            )
        result = self._products(db, history, limit, customer_id=customer_id, as_of=as_of, familiar=familiar)
        return response_model(
            customer_id=customer_id, status="ELIGIBLE",
            valid_order_count=history.valid_order_count, product=result,
        )

    def _products(self, db: Session, history: ExploreHistory, limit: int, *,
                  customer_id: str, as_of: datetime, familiar: bool) -> ExploreProducts:
        products = self.catalog_repository.active_catalog(db)
        commercial_ids = self.history_repository.commercial_product_ids(db)
        if familiar:
            # Only receipt lines with units remaining after recorded returns contribute to product_counts.
            candidate_ids = commercial_ids & history.product_counts.keys()
        else:
            candidate_ids = commercial_ids - history.purchased_ids
        candidates = {product.product_id: product for product in products if product.product_id in candidate_ids}
        if not candidates:
            return ExploreProducts(status="NO_CANDIDATES")
        try:
            artifact = self.artifact_store.load()
            if artifact.catalog_sha256 != catalog_fingerprint(products):
                raise V1ModelError("Catalog differs from trained artifact")
            content, cf = artifact.components(history.product_counts)
            scores, both_flat = hybrid_scores(content, cf, tuple(candidates))
        except V1ModelError:
            return ExploreProducts(status="MODEL_UNAVAILABLE")

        # A flat normalized model cannot establish a personalized ranking in this candidate set.
        if both_flat:
            return ExploreProducts(status="NO_PERSONALIZED_SIGNAL")
        taste = (self.taste_features.prepare(customer_id, as_of, set(scores))
                 if self.taste_features else TasteFeatureBatch("NOT_CONFIGURED"))
        features = CandidateFeatures(
            tuple(BehavioralCandidate(p, score, content[p] > 0 or cf[p] > 0) for p, score in scores.items()),
            taste,
        )
        ranked = self.ranker(features, min(limit, 5))
        return ExploreProducts(
            status="PERSONALIZED", model_contract_version=CONTRACT_VERSION, trained_at=artifact.created_at,
            items=[RecommendationItem(rank=rank, product_id=row.product_id,
                                      product_code=candidates[row.product_id].product_code,
                                      name=candidates[row.product_id].name, score=row.score)
                   for rank, row in enumerate(ranked, 1)],
        )


class ExploreRecommendationService(PersonalizedRecommendationService):
    def recommend(self, db: Session, customer_id: str, limit: int) -> ExploreRecommendationResponse:
        return self._recommend(db, customer_id, limit, ExploreRecommendationResponse, familiar=False)


class FamiliarRecommendationService(PersonalizedRecommendationService):
    def recommend(self, db: Session, customer_id: str, limit: int) -> FamiliarRecommendationResponse:
        return self._recommend(db, customer_id, limit, FamiliarRecommendationResponse, familiar=True)
