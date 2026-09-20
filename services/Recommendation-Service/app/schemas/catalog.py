from decimal import Decimal
from typing import Generic, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class PageResponse(BaseModel, Generic[T]):
    content: list[T]
    page: int
    size: int
    totalElements: int
    totalPages: int
    first: bool
    last: bool


class CategoryResponse(BaseModel):
    id: str
    parentId: str | None = None
    name: str
    status: str
    categoryCode: str
    defaultReorderPoint: int | None = None


class ProductVariantResponse(BaseModel):
    id: str
    productId: str
    productName: str | None = None
    productCode: str | None = None
    sku: str
    variantName: str
    categoryName: str | None = None
    sellingPrice: Decimal
    status: str
    productStatus: str | None = None
    sellable: bool | None = None


class ProductResponse(BaseModel):
    id: str
    category: CategoryResponse
    productCode: str
    name: str
    description: str | None = None
    imageUrl: str | None = None
    status: str
    variants: list[ProductVariantResponse] = []


class IngredientResponse(BaseModel):
    id: str
    ingredientCode: str
    name: str
    status: str
    imageUrl: str | None = None
    unitId: str | None = None
    unitName: str | None = None
    unitSymbol: str | None = None
    reorderPoint: int | None = None


class UnitResponse(BaseModel):
    id: str
    name: str
    symbol: str


class BomItemResponse(BaseModel):
    id: str
    ingredient: IngredientResponse
    quantity: Decimal
    unit: UnitResponse | None = None
    wasteRate: Decimal | None = None


class BomResponse(BaseModel):
    id: str
    productVariantId: str
    variantSku: str | None = None
    variantName: str | None = None
    productName: str | None = None
    name: str | None = None
    bomCode: str | None = None
    isDefault: bool
    version: int
    status: str
    items: list[BomItemResponse]