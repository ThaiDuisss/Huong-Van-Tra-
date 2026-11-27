from decimal import Decimal
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.taste_catalog import Digest


class BomHistoryLine(BaseModel):
    orderItemId: UUID
    variantId: UUID
    retainedQuantity: Decimal
    sourceQuality: Literal["CURRENT_BOM_PROXY", "UNRESOLVED", "FULLY_RETURNED"]
    compositionRevision: int | None = None
    compositionSha256: Digest | None = None


class BundleIngredient(BaseModel):
    ingredientId: UUID
    ingredientCode: str
    ingredientName: str
    quantity: Decimal = Field(gt=0, allow_inf_nan=False)
    unitId: UUID
    unitSymbol: str
    retailPricePerUnit: Decimal = Field(gt=0, allow_inf_nan=False)


class BundlePrefill(BaseModel):
    bundleName: str
    lines: list[BundleIngredient]


class BomQuantityBasis(BaseModel):
    kind: Literal["CATALOG_BOM_ORIGINAL_INPUT"] = "CATALOG_BOM_ORIGINAL_INPUT"
    ingredientInputGrams: Decimal = Field(gt=0, allow_inf_nan=False)


class BomIngredientStock(BaseModel):
    ingredientId: UUID
    requiredQuantity: Decimal = Field(gt=0, allow_inf_nan=False)
    availableQuantity: Decimal = Field(ge=0, allow_inf_nan=False)
    missingQuantity: Decimal = Field(ge=0, allow_inf_nan=False)


class BomAvailability(BaseModel):
    branchId: UUID | None
    enough: Literal[True] = True
    lines: list[BomIngredientStock]


class BomRecommendationItem(BaseModel):
    rank: int = Field(ge=1, le=5)
    variantId: UUID
    sku: str
    compositionRevision: int = Field(gt=0)
    compositionSha256: Digest
    ingredientSimilarity: float = Field(ge=0, le=1, allow_inf_nan=False)
    tasteStatus: str
    tasteScore: float | None = Field(default=None, ge=0, le=1, allow_inf_nan=False)
    finalScore: float = Field(ge=0, le=1, allow_inf_nan=False)
    matchingIngredients: list[str]
    reason: str
    quantityBasis: BomQuantityBasis
    availability: BomAvailability
    bundlePrefill: BundlePrefill


class BomRecommendations(BaseModel):
    customerId: UUID
    validOrderCount: int = Field(ge=0)
    status: Literal["OK", "INSUFFICIENT_VALID_ORDERS", "INSUFFICIENT_INGREDIENT_SIGNAL",
                    "CANDIDATE_POOL_UNAVAILABLE", "NO_AVAILABLE_CANDIDATES",
                    "CATALOG_UNAVAILABLE", "AVAILABILITY_UNAVAILABLE", "SELECTION_STALE"]
    sourceQuality: Literal["CURRENT_BOM_PROXY", "NONE"] = "NONE"
    history: list[BomHistoryLine] = Field(default_factory=list)
    ingredientVector: dict[UUID, float] = Field(default_factory=dict)
    items: list[BomRecommendationItem] = Field(default_factory=list, max_length=5)
