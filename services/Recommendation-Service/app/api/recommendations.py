from functools import lru_cache

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.database.session import get_db
from app.schemas.recommendation import RecommendationResponse
from app.service.recommendation_service import RecommendationService
from app.training.model_artifact import ModelArtifactStore


router = APIRouter(prefix="/recommendations", tags=["recommendations"])


@lru_cache
def get_recommendation_service() -> RecommendationService:
    settings = get_settings()
    return RecommendationService(
        artifact_store=ModelArtifactStore(settings.model_artifact_path)
    )


@router.get("/{customer_id}", response_model=RecommendationResponse)
def recommend_products(
    customer_id: str,
    limit: int | None = Query(default=None, ge=1),
    db: Session = Depends(get_db),
    service: RecommendationService = Depends(get_recommendation_service),
) -> RecommendationResponse:
    settings = get_settings()
    effective_limit = limit or settings.recommendation_default_limit
    effective_limit = min(effective_limit, settings.recommendation_max_limit)
    return service.recommend(
        db,
        customer_id=customer_id.strip(),
        limit=effective_limit,
    )
