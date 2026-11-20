from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.recommendation import RecommendationItem


class ExploreProducts(BaseModel):
    status: Literal["PERSONALIZED", "NO_CANDIDATES", "MODEL_UNAVAILABLE", "NO_PERSONALIZED_SIGNAL"]
    model_contract_version: str | None = Field(default=None, serialization_alias="modelContractVersion")
    trained_at: datetime | None = Field(default=None, serialization_alias="trainedAt")
    items: list[RecommendationItem] = Field(default_factory=list)


class ExploreBoms(BaseModel):
    status: Literal["BOM_MODEL_UNAVAILABLE"] = "BOM_MODEL_UNAVAILABLE"
    items: list[object] = Field(default_factory=list, max_length=0)


class PersonalizedRecommendationResponse(BaseModel):
    customer_id: str = Field(serialization_alias="customerId")
    mode: Literal["EXPLORE", "FAMILIAR"]
    status: Literal["ELIGIBLE", "INSUFFICIENT_VALID_ORDERS"]
    valid_order_count: int = Field(serialization_alias="validOrderCount")
    product: ExploreProducts | None = None
    bom: ExploreBoms = Field(default_factory=ExploreBoms)


class ExploreRecommendationResponse(PersonalizedRecommendationResponse):
    mode: Literal["EXPLORE"] = "EXPLORE"


class FamiliarRecommendationResponse(PersonalizedRecommendationResponse):
    mode: Literal["FAMILIAR"] = "FAMILIAR"
