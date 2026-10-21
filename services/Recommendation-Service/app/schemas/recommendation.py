from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class RecommendationMode(str, Enum):
    DISCOVERY = "DISCOVERY"
    REPEAT = "REPEAT"


class RecommendationItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    rank: int
    product_id: str = Field(serialization_alias="productId")
    product_code: str = Field(serialization_alias="productCode")
    name: str
    score: float


class RecommendationResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    customer_id: str = Field(serialization_alias="customerId")
    source: Literal["MODEL", "POPULARITY", "HYBRID", "FALLBACK"]
    fallback_reason: str | None = Field(
        default=None,
        serialization_alias="fallbackReason",
    )
    model_type: str | None = Field(default=None, serialization_alias="modelType")
    model_contract_version: str | None = Field(
        default=None,
        serialization_alias="modelContractVersion",
    )
    trained_at: datetime | None = Field(default=None, serialization_alias="trainedAt")
    items: list[RecommendationItem]
    purchase_order_count: int | None = Field(default=None, serialization_alias="purchaseOrderCount")
    exclude_purchased: bool = Field(default=True, serialization_alias="excludePurchased")
    mode: RecommendationMode | None = None
    recommendation_event_id: str | None = Field(default=None, serialization_alias="recommendationEventId")
    generated_at: datetime | None = Field(default=None, serialization_alias="generatedAt")
