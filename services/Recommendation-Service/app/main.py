from fastapi import FastAPI

from app.api.ai_reports import router as ai_reports_router

from app.api.catalog_sync import router as catalog_sync_router
from app.api.health import router as health_router
from app.api.recommendations import router as recommendations_router
from app.api.taste_recommendations import router as taste_recommendations_router
from app.core.config import get_settings
from app.core.recommendation_auth import validate_security
from app.service.taste_catalog_refresh import taste_catalog_lifespan


def create_app() -> FastAPI:
    settings = get_settings()
    validate_security(settings)
    application = FastAPI(title=settings.app_name, lifespan=taste_catalog_lifespan)
    application.include_router(health_router)
    application.include_router(ai_reports_router)
    application.include_router(catalog_sync_router)
    application.include_router(recommendations_router)
    application.include_router(taste_recommendations_router)
    return application


app = create_app()
