from functools import lru_cache

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response
from uuid import UUID
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.database.session import get_db
from app.schemas.recommendation import RecommendationMode, RecommendationResponse
from app.core.recommendation_auth import authorize_customer
from app.service.recommendation_service import RecommendationService
from app.recommendation.v1_artifact import V1ArtifactStore
from app.service.recommendation_snapshot_service import RecommendationSnapshotService


router = APIRouter(prefix="/recommendations", tags=["recommendations"])


def get_snapshot_service():
    return RecommendationSnapshotService()


@lru_cache
def get_recommendation_service() -> RecommendationService:
    settings = get_settings()
    return RecommendationService(
        artifact_store=V1ArtifactStore(settings.v1_artifact_path)
    )


@router.get("/{customer_id}", response_model=RecommendationResponse)
def recommend_products(
    customer_id: str,
    response: Response,
    limit: int | None = Query(default=None, ge=1),
    exclude_purchased: bool | None = Query(default=None),
    mode: RecommendationMode | None = Query(default=None),
    idempotency_key: UUID | None = Header(default=None, alias="Idempotency-Key"),
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
    service: RecommendationService = Depends(get_recommendation_service),
    snapshots: RecommendationSnapshotService = Depends(get_snapshot_service),
) -> RecommendationResponse:
    settings = get_settings()
    response.headers["Cache-Control"] = "private, no-store"
    if mode is not None and exclude_purchased is not None:
        if mode != RecommendationMode.DISCOVERY or not exclude_purchased:
            raise HTTPException(422, "Use mode without exclude_purchased; DISCOVERY + true is the only compatible combination")
    effective_limit = limit or settings.recommendation_default_limit
    effective_limit = min(effective_limit, settings.recommendation_max_limit)
    effective_limit = min(effective_limit, 5)
    return snapshots.issue(
        db,
        service,
        customer_id=verified_customer,
        limit=effective_limit,
        exclude_purchased=True if exclude_purchased is None else exclude_purchased,
        mode=mode,
        key=str(idempotency_key) if idempotency_key else None,
    )
