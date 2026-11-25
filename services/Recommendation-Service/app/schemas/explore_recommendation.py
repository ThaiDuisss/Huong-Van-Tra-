from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

from app.schemas.recommendation import RecommendationItem


class ServingDetails(BaseModel):
    algorithm: str | None = None
    model_version: str | None = Field(default=None, serialization_alias="modelVersion")
    outcome_horizon: int | None = Field(default=None, serialization_alias="outcomeHorizon")
    candidate_count: int = Field(serialization_alias="candidateCount")
    returned_count: int = Field(default=0, serialization_alias="returnedCount")
    fallback_applied: bool = Field(default=False, serialization_alias="fallbackApplied")
    fallback_reason: str | None = Field(default=None, serialization_alias="fallbackReason")
    taste_status: str | None = Field(default=None, serialization_alias="tasteStatus")
    repeat_interval_count: int | None = Field(default=None, serialization_alias="repeatIntervalCount")
    data_source: Literal["HISTORICAL_SNAPSHOT", "LIVE_ISOLATED"] = Field(
        default="HISTORICAL_SNAPSHOT", serialization_alias="dataSource",
    )
    serving_provenance: Literal["SIMULATED_DEMO"] = Field(serialization_alias="servingProvenance")
    cutoff: datetime


class Level2Eligibility(BaseModel):
    customer_id: str = Field(serialization_alias="customerId")
    enabled: bool
    valid_order_count: int | None = Field(default=None, serialization_alias="validOrderCount")


class ExploreProducts(BaseModel):
    status: Literal["PERSONALIZED", "NO_CANDIDATES", "MODEL_UNAVAILABLE",
                    "NO_PERSONALIZED_SIGNAL", "AVAILABILITY_UNAVAILABLE"]
    model_contract_version: str | None = Field(default=None, serialization_alias="modelContractVersion")
    trained_at: datetime | None = Field(default=None, serialization_alias="trainedAt")
    items: list[RecommendationItem] = Field(default_factory=list)
    serving: ServingDetails | None = None


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
