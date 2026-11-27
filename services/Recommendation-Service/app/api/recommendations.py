from datetime import datetime, timezone
from functools import lru_cache

from fastapi import APIRouter, BackgroundTasks, Depends, Header, HTTPException, Query, Request, Response
from uuid import UUID, uuid4
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from sqlalchemy.exc import SQLAlchemyError

from app.clients.customer_taste_client import CustomerTasteClient
from app.core.config import get_settings
from app.database.session import SessionLocal, get_db
from app.repositories.explore_history_repository import ExploreHistoryError, ExploreHistoryRepository
from app.schemas.bom_recommendation import BomRecommendations
from app.schemas.ingredient_recommendation import IngredientRecommendations
from app.models.business_report import IngredientRecommendationLog
from app.schemas.taste_catalog import Digest
from app.schemas.explore_recommendation import (
    ExploreRecommendationResponse, FamiliarRecommendationResponse, Level2Eligibility,
)
from app.schemas.recommendation import (
    GuestRecommendationResponse, RecommendationItem, RecommendationMode, RecommendationResponse,
)
from app.core.recommendation_auth import authorize_customer, authorize_pos_actor
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.service.explore_recommendation_service import ExploreRecommendationService, FamiliarRecommendationService
from app.service.bom_recommendation_service import BomRecommendationService
from app.service.ingredient_recommendation_service import IngredientRecommendationService
from app.service.level2_availability import Level2Availability
from app.service.level2_serving_inputs import get_level2_inputs
from app.service.personalized_taste_features import PersonalizedTasteFeatures
from app.service.recommendation_service import RecommendationService
from app.recommendation.v1_artifact import V1ArtifactStore
from app.service.recommendation_snapshot_service import RecommendationSnapshotService, utc_now
from app.service.taste_catalog_sync_service import get_taste_catalog_sync


router = APIRouter(prefix="/recommendations", tags=["recommendations"])


def get_snapshot_service():
    return RecommendationSnapshotService()


@lru_cache
def get_recommendation_service() -> RecommendationService:
    settings = get_settings()
    return RecommendationService(
        artifact_store=V1ArtifactStore(settings.v1_artifact_path)
    )


@lru_cache
def get_personalized_taste_features() -> PersonalizedTasteFeatures:
    return PersonalizedTasteFeatures(CustomerTasteClient(get_settings()), get_taste_catalog_sync().read)


def _serving_taste_features() -> PersonalizedTasteFeatures:
    inputs = get_level2_inputs()
    return PersonalizedTasteFeatures(
        inputs.customer_taste, inputs.projection, latest_survey=inputs.survey,
    )


def get_explore_recommendation_service(request: Request) -> ExploreRecommendationService:
    rerankers = getattr(request.app.state, "taste_rerankers", {})
    return ExploreRecommendationService(
        get_recommendation_service().artifact_store, taste_features=_serving_taste_features(),
        availability=Level2Availability(get_settings()),
        serving_inputs=get_level2_inputs(),
        taste_reranker=rerankers.get("EXPLORE"),
    )


def get_familiar_recommendation_service(request: Request) -> FamiliarRecommendationService:
    rerankers = getattr(request.app.state, "taste_rerankers", {})
    return FamiliarRecommendationService(
        get_recommendation_service().artifact_store, taste_features=_serving_taste_features(),
        availability=Level2Availability(get_settings()),
        serving_inputs=get_level2_inputs(),
        taste_reranker=rerankers.get("FAMILIAR"),
    )


@router.get("/{customer_id}/eligibility", response_model=Level2Eligibility)
def level2_eligibility(
    request: Request,
    response: Response,
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
) -> Level2Eligibility:
    response.headers["Cache-Control"] = "private, no-store"
    demo = getattr(request.app.state, "level2_demo", None)
    try:
        if demo is not None:
            count = demo.valid_orders(db, verified_customer)
        else:
            count = get_level2_inputs().product_history(
                ExploreHistoryRepository(), db, verified_customer, datetime.now(timezone.utc).replace(tzinfo=None)
            ).valid_order_count
    except (ExploreHistoryError, SQLAlchemyError):
        raise HTTPException(503, "Personalized purchase evidence unavailable",
                            headers={"Cache-Control": "private, no-store"}) from None
    return Level2Eligibility(customer_id=verified_customer, enabled=True, valid_order_count=count)


@router.get("/{customer_id}/explore", response_model=ExploreRecommendationResponse)
def explore_products(
    customer_id: str,
    request: Request,
    response: Response,
    limit: int = Query(default=5, ge=1, le=5),
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
    service: ExploreRecommendationService = Depends(get_explore_recommendation_service),
) -> ExploreRecommendationResponse:
    response.headers["Cache-Control"] = "private, no-store"
    try:
        demo = getattr(request.app.state, "level2_demo", None)
        if demo is not None:
            if request_id := request.headers.get("X-Request-ID"):
                response.headers["X-Request-ID"] = request_id
            return demo.recommend(ExploreRecommendationResponse, verified_customer, "EXPLORE", limit, db=db)
        return service.recommend(db, verified_customer, limit)
    except (SQLAlchemyError, ExploreHistoryError):
        raise HTTPException(503, "Personalized purchase evidence unavailable",
                            headers={"Cache-Control": "private, no-store"}) from None


@router.get("/{customer_id}/familiar", response_model=FamiliarRecommendationResponse)
def familiar_products(
    customer_id: str,
    request: Request,
    response: Response,
    limit: int = Query(default=5, ge=1, le=5),
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
    service: FamiliarRecommendationService = Depends(get_familiar_recommendation_service),
) -> FamiliarRecommendationResponse:
    response.headers["Cache-Control"] = "private, no-store"
    try:
        demo = getattr(request.app.state, "level2_demo", None)
        if demo is not None:
            if request_id := request.headers.get("X-Request-ID"):
                response.headers["X-Request-ID"] = request_id
            return demo.recommend(FamiliarRecommendationResponse, verified_customer, "FAMILIAR", limit, db=db)
        return service.recommend(db, verified_customer, limit)
    except (SQLAlchemyError, ExploreHistoryError):
        raise HTTPException(503, "Personalized purchase evidence unavailable",
                            headers={"Cache-Control": "private, no-store"}) from None


def _persist_ingredient_log(model_version: str, items: list[dict]) -> None:
    db = SessionLocal()
    try:
        db.add(IngredientRecommendationLog(
            id=str(uuid4()), generated_at=utc_now(), model_version=model_version, items=items,
        ))
        db.commit()
    except SQLAlchemyError:
        db.rollback()
    finally:
        db.close()


@router.get("/{customer_id}/ingredients", response_model=IngredientRecommendations, response_model_exclude_none=True,
            responses={503: {"model": IngredientRecommendations}})
def recommend_ingredients(
    response: Response,
    background_tasks: BackgroundTasks,
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
    limit: int = Query(default=5, ge=1, le=10),
    selected_ids: list[UUID] | None = Query(default=None, alias="selectedIngredientIds", max_length=100),
    branch_id: UUID | None = Query(default=None, alias="branchId"),
) -> IngredientRecommendations | JSONResponse:
    response.headers["Cache-Control"] = "private, no-store"
    try:
        result = IngredientRecommendationService(get_settings(), get_level2_inputs()).recommend(
            db, verified_customer, limit, branch_id, set(selected_ids or ()),
        )
        if result.status == "UPSTREAM_UNAVAILABLE":
            return JSONResponse(status_code=503, headers={"Cache-Control": "private, no-store"},
                                content=result.model_dump(mode="json", exclude_none=True))
        if result.items:
            logged_items = [
                item.model_dump(mode="json", include={
                    "ingredientId", "ingredientCode", "ingredientName", "evidenceSources",
                })
                for item in result.items
            ]
            if db.get_bind().dialect.name == "sqlite":
                db.add(IngredientRecommendationLog(
                    id=str(uuid4()), generated_at=utc_now(), model_version=result.modelVersion,
                    items=logged_items,
                ))
                db.commit()
            else:
                background_tasks.add_task(_persist_ingredient_log, result.modelVersion, logged_items)
        return result
    except (ExploreHistoryError, SQLAlchemyError, ValueError):
        db.rollback()
        raise HTTPException(503, "Ingredient recommendation evidence unavailable",
                            headers={"Cache-Control": "private, no-store"}) from None


@router.get("/{customer_id}/boms", response_model=BomRecommendations, response_model_exclude_none=True)
def recommend_boms(
    response: Response,
    verified_customer: str = Depends(authorize_customer),
    db: Session = Depends(get_db),
    limit: int = Query(default=5, ge=1, le=5),
    branch_id: UUID | None = Query(default=None, alias="branchId"),
    variant_id: UUID | None = Query(default=None, alias="variantId"),
    revision: int | None = Query(default=None, alias="compositionRevision", gt=0),
    checksum: Digest | None = Query(default=None, alias="compositionSha256"),
) -> BomRecommendations:
    response.headers["Cache-Control"] = "private, no-store"
    if any(value is not None for value in (variant_id, revision, checksum)):
        if variant_id is None or revision is None or checksum is None:
            raise HTTPException(422, "Complete BOM composition identity required",
                                headers={"Cache-Control": "private, no-store"})
        selected = (variant_id, revision, checksum)
    else:
        selected = None
    try:
        return BomRecommendationService(get_settings(), get_personalized_taste_features()).recommend(
            db, verified_customer, limit, branch_id, selected,
        )
    except (ExploreHistoryError, SQLAlchemyError):
        raise HTTPException(503, "Personalized purchase evidence unavailable",
                            headers={"Cache-Control": "private, no-store"}) from None


@router.get("/guest", response_model=GuestRecommendationResponse)
def recommend_guest_products(
    response: Response,
    limit: int = Query(default=5, ge=1),
    actor: str = Depends(authorize_pos_actor),
    db: Session = Depends(get_db),
) -> GuestRecommendationResponse:
    response.headers["Cache-Control"] = "private, no-store"
    try:
        rows = V1RecommendationRepository().popularity(db, set(), min(limit, 5))
    except SQLAlchemyError:
        raise HTTPException(503, "Guest recommendations unavailable") from None
    return GuestRecommendationResponse(items=[
        RecommendationItem(rank=rank, product_id=product_id, product_code=code,
                           name=name, score=float(score))
        for rank, (product_id, code, name, score) in enumerate(rows, 1)
    ])


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
