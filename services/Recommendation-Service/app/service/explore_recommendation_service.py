from dataclasses import dataclass
from datetime import datetime, timezone
import logging
from typing import Callable, TypeVar

from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.recommendation.taste_ranking_artifact import MODEL_CONTRACT, TasteReranker
from app.recommendation.taste_ranking_features import MIN_VALID_ORDERS, Mode, eligible_products, ranking_features
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

MIN_EXPLORE_VALID_ORDERS = MIN_VALID_ORDERS
ResponseT = TypeVar("ResponseT", bound=PersonalizedRecommendationResponse)
logger = logging.getLogger(__name__)


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
                 taste_reranker: TasteReranker | None = None,
                 ranker: Callable[[CandidateFeatures, int], list[BehavioralCandidate]] = rank_by_behavior) -> None:
        self.artifact_store = artifact_store
        self.history_repository = history_repository or ExploreHistoryRepository()
        self.catalog_repository = catalog_repository or V1RecommendationRepository()
        self.taste_features = taste_features
        self.taste_reranker = taste_reranker or TasteReranker()
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
        mode: Mode = "FAMILIAR" if familiar else "EXPLORE"
        candidate_ids = eligible_products(mode, commercial_ids, history)
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
        try:
            taste = (self.taste_features.prepare(customer_id, as_of, set(scores))
                     if self.taste_features else TasteFeatureBatch("NOT_CONFIGURED"))
        except (SQLAlchemyError, RuntimeError, ValueError):
            taste = TasteFeatureBatch("CATALOG_UNAVAILABLE")
        features = CandidateFeatures(
            tuple(BehavioralCandidate(p, score, content[p] > 0 or cf[p] > 0) for p, score in scores.items()),
            taste,
        )
        top_k = min(limit, 5)
        model_contract, trained_at = CONTRACT_VERSION, artifact.created_at
        learned = self.taste_reranker.artifact
        if learned and learned.mode == mode:
            rows = tuple(ranking_features(candidates[p], history, as_of, score, content[p], cf[p], taste)
                         for p, score in scores.items())
            result = self.taste_reranker.score(rows, taste)
            logger.debug("taste_ranking mode=%s status=%s", mode, result.status)
            if result.scores is not None:
                ranked = [BehavioralCandidate(p, score, True)
                          for p, score in sorted(result.scores.items(), key=lambda row: (-row[1], row[0]))[:top_k]]
                model_contract, trained_at = MODEL_CONTRACT, learned.trained_at
            else:
                ranked = self.ranker(features, top_k)
        else:
            status = "ARTIFACT_MODE_INCOMPATIBLE" if learned else self.taste_reranker.status
            logger.debug("taste_ranking mode=%s status=%s", mode, status)
            ranked = self.ranker(features, top_k)
        return ExploreProducts(
            status="PERSONALIZED", model_contract_version=model_contract, trained_at=trained_at,
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
