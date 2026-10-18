from functools import lru_cache

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.database.session import get_db
from app.schemas.recommendation import RecommendationResponse
from app.service.recommendation_service import RecommendationService
from app.recommendation.v1_artifact import V1ArtifactStore


router = APIRouter(prefix="/recommendations", tags=["recommendations"])


@lru_cache
def get_recommendation_service() -> RecommendationService:
    settings = get_settings()
    return RecommendationService(
        artifact_store=V1ArtifactStore(settings.v1_artifact_path)
    )


@router.get("/{customer_id}", response_model=RecommendationResponse)
def recommend_products(
    customer_id: str,
    limit: int | None = Query(default=None, ge=1),
    exclude_purchased: bool = Query(default=True),
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
        exclude_purchased=exclude_purchased,
    )
