"""Run explicitly: uvicorn app.ingredient_demo.api:create_app --factory --host 127.0.0.1."""
from contextlib import asynccontextmanager
from datetime import datetime
import hmac
from io import BytesIO
from typing import Literal

from fastapi import Depends, FastAPI, HTTPException, Query, Request, Response
from fastapi.responses import StreamingResponse
from pydantic import ValidationError

from app.ingredient_demo.service import ArtifactVerificationError, IngredientDemoService
from app.ingredient_demo.settings import DemoConfigurationError, IngredientDemoSettings


def create_app(settings: IngredientDemoSettings | None = None) -> FastAPI:
    try:
        config = settings if settings is not None else IngredientDemoSettings()
        config.validate_demo()
    except (ValidationError, DemoConfigurationError):
        raise DemoConfigurationError("Invalid standalone ingredient demo configuration") from None

    @asynccontextmanager
    async def lifespan(application):
        if config.demo_enabled:
            application.state.ingredient_service = IngredientDemoService(config)
        yield
        application.state.ingredient_service = None

    application = FastAPI(title="Ingredient Recommendation Research Demo", lifespan=lifespan,
                          docs_url=None, redoc_url=None, openapi_url=None)

    def authorized_service(request: Request):
        if not config.demo_enabled:
            raise HTTPException(404, "Ingredient demo is disabled")
        headers = request.headers.getlist("authorization")
        expected = config.demo_token.get_secret_value()
        if (len(headers) != 1 or not headers[0].startswith("Bearer ")
                or not hmac.compare_digest(headers[0][7:].encode(), expected.encode())):
            raise HTTPException(401, "Invalid demo credentials", headers={"WWW-Authenticate": "Bearer"})
        service = getattr(request.app.state, "ingredient_service", None)
        if service is None:
            raise HTTPException(503, "Ingredient demo is not ready")
        try:
            service.verify_integrity()
        except ArtifactVerificationError:
            raise HTTPException(503, "Ingredient research artifacts failed verification") from None
        return service

    @application.middleware("http")
    async def private_responses(request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "private, no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    prefix = "/api/v1/ingredient-demo"

    @application.get(prefix + "/customers")
    def customers(service=Depends(authorized_service)):
        return service.customers()

    @application.get(prefix + "/recommendations")
    def recommendations(customer_id: str, as_of: datetime, response: Response,
                        mode: Literal["PRODUCT", "CUSTOM_BOM", "UNIFIED"] = "UNIFIED",
                        policy: Literal["DISCOVERY", "REPEAT"] = "DISCOVERY",
                        top_k: int = Query(5, ge=1, le=5), service=Depends(authorized_service)):
        try:
            return service.recommend(customer_id, as_of=as_of, mode=mode, policy=policy, top_k=top_k)
        except KeyError:
            raise HTTPException(404, "Customer is not in the research dataset") from None
        except ValueError:
            raise HTTPException(422, "Invalid research window or inference parameters") from None

    @application.get(prefix + "/reports/excel")
    def excel(service=Depends(authorized_service)):
        # Fixed TEST protocol, independent of the wall clock and request parameters.
        filename = "Ingredient_Recommendation_Research_20260401.xlsx"
        return StreamingResponse(BytesIO(service.excel()),
            media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            headers={"Content-Disposition": f'attachment; filename="{filename}"'})

    return application
