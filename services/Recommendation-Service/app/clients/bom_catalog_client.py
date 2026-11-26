from dataclasses import dataclass
from decimal import Decimal, DecimalException
from fractions import Fraction
from math import sqrt
from uuid import UUID

import httpx
from pydantic import BaseModel, Field

from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings
from app.schemas.bom_recommendation import BundleIngredient
from app.schemas.taste_catalog import Digest, digest


class CatalogBomUnavailable(RuntimeError):
    pass


class RecipeRow(BaseModel):
    variantId: UUID
    productId: UUID
    sku: str
    name: str
    compositionRevision: int = Field(gt=0)
    compositionSha256: Digest
    basis: dict


class UnitInfo(BaseModel):
    symbol: str
    precisionScale: int = Field(ge=0)


class IngredientInfo(BaseModel):
    code: str
    name: str
    retailPricePerUnit: Decimal | None = Field(default=None, allow_inf_nan=False)


class RecipeBatch(BaseModel):
    recipes: list[RecipeRow]
    ingredients: dict[UUID, IngredientInfo]
    units: dict[UUID, UnitInfo]


@dataclass(frozen=True)
class BomRecipe:
    row: RecipeRow
    grams: dict[UUID, Decimal]
    names: dict[UUID, str]
    lines: tuple[BundleIngredient, ...] | None

    @property
    def ratio(self) -> tuple[tuple[UUID, Fraction], ...]:
        total = sum(map(Fraction, self.grams.values()))
        return tuple((ingredient, Fraction(grams) / total) for ingredient, grams in sorted(self.grams.items()))

    @property
    def vector(self) -> dict[UUID, float]:
        return {ingredient: sqrt(float(ratio)) for ingredient, ratio in self.ratio}


def _unit_chain(raw: list) -> tuple[UUID, UUID, Decimal]:
    seen = set()
    factor = Decimal(1)
    if not raw or len(raw) > 16:
        raise ValueError("Invalid unit chain")
    for index, unit in enumerate(raw):
        identity = UUID(unit["unitId"])
        if identity in seen or unit.get("status") != "ACTIVE" or unit.get("deleted") is not False:
            raise ValueError("Invalid unit in chain")
        seen.add(identity)
        conversion = unit.get("conversionRate")
        if index < len(raw) - 1:
            rate = Decimal(conversion)
            if not rate.is_finite() or rate <= 0:
                raise ValueError("Invalid unit conversion")
            factor *= rate
        elif conversion is not None or unit["type"] != "WEIGHT":
            raise ValueError("Invalid mass root")
    return UUID(raw[0]["unitId"]), identity, factor


def normalize_recipe(row: RecipeRow, batch: RecipeBatch) -> BomRecipe:
    basis = row.basis
    if (str(row.variantId) != basis.get("variantId") or basis.get("ruleVersion") != 1
            or digest(basis) != row.compositionSha256):
        raise ValueError("Composition identity mismatch")

    grams: dict[UUID, Decimal] = {}
    names = {}
    lines = []
    for item in basis["ingredients"]:
        ingredient_id = UUID(item["ingredientId"])
        if item["kind"] == 0:
            continue
        if (item["kind"] != 1 or item["deleted"] is not False or item["status"] != "ACTIVE"
                or ingredient_id in grams):
            raise ValueError("Invalid or duplicate ingredient")
        _, root, amount_factor = _unit_chain(item["unit"])
        stock_id, stock_root, stock_factor = _unit_chain(item["ingredientUnit"])
        if root != stock_root or batch.units[root].symbol != "g":
            raise ValueError("No shared gram root")
        value = Decimal(item["quantity"]) * amount_factor
        if not value.is_finite() or not 0 < value <= 10**9:
            raise ValueError("Invalid ingredient quantity")
        grams[ingredient_id] = value
        meta = batch.ingredients[ingredient_id]
        unit = batch.units[stock_id]
        names[ingredient_id] = meta.name
        amount = value / stock_factor
        if (meta.retailPricePerUnit is not None and meta.retailPricePerUnit > 0 and amount < 10**11
                and amount == amount.quantize(Decimal(1).scaleb(-min(unit.precisionScale, 3)))):
            lines.append(BundleIngredient(
                ingredientId=ingredient_id, ingredientCode=meta.code, ingredientName=meta.name,
                quantity=amount, unitId=stock_id, unitSymbol=unit.symbol, retailPricePerUnit=meta.retailPricePerUnit,
            ))
    if not grams:
        raise ValueError("No edible ingredients")
    prefill = tuple(sorted(lines, key=lambda line: line.ingredientId)) if len(lines) == len(grams) <= 10 else None
    return BomRecipe(row, grams, names, prefill)


def fetch_bom_recipes(settings: Settings, ids: set[UUID]) -> dict[UUID, BomRecipe]:
    if not ids:
        return {}
    result = {}
    targets = sorted(ids)
    try:
        with httpx.Client(base_url=settings.catalog_service_url,
                          timeout=settings.level2_stock_timeout_seconds, trust_env=False) as client:
            for start in range(0, len(targets), 64):
                requested = targets[start:start + 64]
                res = client.get("/internal/catalog/bom-recipes",
                                 params={"variantIds": ",".join(map(str, requested))},
                                 headers=generate_internal_auth_headers())
                res.raise_for_status()
                batch = RecipeBatch.model_validate_json(res.content)
                seen = {row.variantId for row in batch.recipes}
                if len(seen) != len(batch.recipes) or not seen.issubset(requested):
                    raise ValueError("Invalid Catalog recipe batch")
                for row in batch.recipes:
                    try:
                        result[row.variantId] = normalize_recipe(row, batch)
                    except (ValueError, KeyError, TypeError, DecimalException):
                        # One incomplete recipe cannot make an unrelated recipe eligible.
                        continue
    except (httpx.HTTPError, ValueError) as exc:
        raise CatalogBomUnavailable("CATALOG_UNAVAILABLE") from exc
    return result
