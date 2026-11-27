from decimal import Decimal
from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, Field, PlainSerializer

EvidenceSource = Literal["PRODUCT_BOM_PROXY", "DIRECT_CUSTOM_BUNDLE_COMPONENT",
                         "CATALOG_BOM_CO_OCCURRENCE", "CATALOG_BOM_PRIOR"]
Score = Annotated[float, Field(ge=0, le=1, allow_inf_nan=False)]
PositiveDecimal = Annotated[Decimal, Field(gt=0, allow_inf_nan=False), PlainSerializer(float, when_used="json")]


class IngredientRecommendationItem(BaseModel):
    ingredientId: UUID
    ingredientCode: str
    ingredientName: str
    unitId: UUID
    unitSymbol: str
    retailPricePerUnit: PositiveDecimal
    availableQuantity: PositiveDecimal
    behavioralScore: Score
    pairScore: Score
    priorScore: Score
    finalScore: Score
    reason: str
    evidenceSources: list[EvidenceSource]
    sourceQuality: Literal["CURRENT_BOM_PROXY"] | None = None


class IngredientRecommendations(BaseModel):
    customerId: UUID
    status: Literal["OK", "INSUFFICIENT_VALID_ORDERS", "NO_INGREDIENT_EVIDENCE",
                    "NO_AVAILABLE_INGREDIENTS", "FALLBACK_SIMILAR_RETAIL", "UPSTREAM_UNAVAILABLE"]
    validOrderCount: int = Field(ge=0)
    modelVersion: Literal["hvtra-ingredient-baseline-v1"] = "hvtra-ingredient-baseline-v1"
    items: list[IngredientRecommendationItem] = Field(default_factory=list, max_length=10)
    unavailableSource: Literal["CATALOG", "INVENTORY"] | None = None
