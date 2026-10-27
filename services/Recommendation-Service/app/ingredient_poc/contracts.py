"""Pure input/hash contracts. No importer, model training, serving or I/O."""
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP, localcontext
import hashlib
import json
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

SourceKind = Literal["SYNTHETIC", "STANDARD_CURRENT_PROXY", "ACTUAL"]


class Contract(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, allow_inf_nan=False)


class UnitContract(Contract):
    symbol: str = Field(min_length=1, max_length=32)
    dimension: Literal["MASS", "VOLUME", "COUNT"]
    canonical_unit: Literal["g", "ml", "count"] | None = None
    conversion_factor: Decimal | None = Field(default=None, gt=0, max_digits=18, decimal_places=8)
    conversion_rule_version: str = Field(min_length=1, max_length=64)

    @model_validator(mode="after")
    def conversion(self):
        if (self.canonical_unit is None) != (self.conversion_factor is None):
            raise ValueError("canonical unit and factor must be paired")
        expected = {"MASS": "g", "VOLUME": "ml", "COUNT": "count"}[self.dimension]
        if self.canonical_unit is not None and self.canonical_unit != expected:
            raise ValueError("cross-dimension conversion requires a separate physical contract")
        if self.dimension == "COUNT" and self.conversion_factor not in (None, Decimal(1)):
            raise ValueError("ambiguous count/package conversion")
        physical = {"g": ("MASS", Decimal(1)), "kg": ("MASS", Decimal(1000)),
                    "ml": ("VOLUME", Decimal(1)), "l": ("VOLUME", Decimal(1000))}
        if self.dimension != "COUNT" and self.conversion_factor is not None:
            if physical.get(self.symbol) != (self.dimension, self.conversion_factor) and not (
                    self.symbol == "L" and self.dimension == "VOLUME" and self.conversion_factor == 1000):
                raise ValueError("unverified physical symbol or scale; quarantine")
        return self


class RecipeItemContract(Contract):
    ingredient_namespace: str = Field(min_length=1, max_length=64)
    ingredient_code: str = Field(min_length=1, max_length=100)
    ingredient_role: str | None = Field(default=None, max_length=32)
    quantity_original: Decimal = Field(gt=0, max_digits=24, decimal_places=8)
    unit: UnitContract
    quantity_canonical: Decimal | None = Field(default=None, gt=0, max_digits=24, decimal_places=8)
    waste_rate: Decimal | None = Field(default=None, ge=0, max_digits=9, decimal_places=4)

    @model_validator(mode="after")
    def canonical(self):
        factor = self.unit.conversion_factor
        if self.quantity_canonical is not None:
            with localcontext() as context:
                context.prec = 64
                expected = (self.quantity_original * factor).quantize(
                    Decimal("0.00000001"), rounding=ROUND_HALF_UP) if factor else None
            if expected != self.quantity_canonical:
                raise ValueError("canonical quantity must exclude waste and use the snapshot factor")
        return self


class RecipeContract(Contract):
    recipe_family_key: str = Field(min_length=1, max_length=128)
    recipe_version: int = Field(gt=0)
    recipe_kind: Literal["STANDARD_CURRENT_PROXY", "CUSTOM"]
    portion_quantity: Decimal | None = Field(default=None, gt=0, max_digits=24, decimal_places=8)
    portion_unit: str | None = Field(default=None, min_length=1, max_length=32)
    servings_per_recipe: Decimal | None = Field(default=None, gt=0, max_digits=24, decimal_places=8)
    effective_time_unknown: bool
    items: tuple[RecipeItemContract, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def consistency(self):
        keys = [(i.ingredient_namespace, i.ingredient_code) for i in self.items]
        if len(set(keys)) != len(keys):
            raise ValueError("duplicate recipe ingredient")
        if (self.portion_quantity is None) != (self.portion_unit is None):
            raise ValueError("portion quantity/unit must be paired")
        if self.recipe_kind == "STANDARD_CURRENT_PROXY" and not self.effective_time_unknown:
            raise ValueError("current BOM does not establish historical effective time")
        return self


def _canonical(value):
    if isinstance(value, Decimal):
        result = format(value, "f")
        result = result.rstrip("0").rstrip(".") if "." in result else result
        return "0" if result in ("-0", "") else result
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, datetime):
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("timestamp must have a timezone")
        return value.astimezone(timezone.utc).isoformat()
    if isinstance(value, dict):
        return {key: _canonical(val) for key, val in value.items()}
    if isinstance(value, (list, tuple)):
        return [_canonical(val) for val in value]
    return value


def content_hash(value):
    return hashlib.sha256(json.dumps(_canonical(value), sort_keys=True,
                                    separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def recipe_hash(recipe: RecipeContract):
    """Version/family are labels; the hash captures exact contents and unit rules."""
    value = recipe.model_dump(exclude={"recipe_family_key", "recipe_version"})
    value["items"] = sorted(value["items"], key=lambda i: (i["ingredient_namespace"], i["ingredient_code"]))
    return content_hash(value)


class SaleContract(Contract):
    dataset_id: UUID
    customer_id: UUID  # Research customer row, not an upstream customer UUID.
    order_id: UUID  # Research order row; its source_order_id is stored separately.
    order_item_id: UUID  # Source line key (or research line key in SYNTHETIC).
    purchase_type: Literal["PRODUCT", "CUSTOM_BOM"]
    product_id: UUID | None = None  # Research dimension IDs.
    variant_id: UUID | None = None
    recipe_id: UUID | None = None
    recipe_kind: Literal["STANDARD_CURRENT_PROXY", "CUSTOM"] | None = None
    actual_recipe_snapshot_id: UUID | None = None
    sale_unit_id: UUID
    quantity: Decimal = Field(gt=0, max_digits=24, decimal_places=8)
    purchased_at: datetime
    order_status: Literal["COMPLETED"]
    payment_status: Literal["PAID"]
    source_kind: SourceKind
    source_system: str = Field(min_length=1, max_length=64)
    source_event_id: UUID | None = None
    source_revision: int = Field(gt=0)

    @model_validator(mode="after")
    def transaction(self):
        if self.purchased_at.tzinfo is None or self.purchased_at.utcoffset() is None:
            raise ValueError("purchase timestamp requires timezone")
        if self.purchase_type == "PRODUCT":
            if self.product_id is None or self.variant_id is None:
                raise ValueError("PRODUCT requires exact research product and variant")
        elif (self.product_id is not None or self.variant_id is not None
              or self.recipe_id is None or self.recipe_kind != "CUSTOM"):
            raise ValueError("CUSTOM_BOM requires a recipe, never a fake Product")
        if (self.recipe_id is None) != (self.recipe_kind is None):
            raise ValueError("recipe reference/kind must be paired")
        if self.source_kind == "STANDARD_CURRENT_PROXY" and self.purchase_type != "PRODUCT":
            raise ValueError("a standard proxy is not an actual custom purchase")
        if self.actual_recipe_snapshot_id is not None and (
                self.source_kind != "ACTUAL" or self.recipe_kind != "CUSTOM"):
            raise ValueError("actual snapshot requires ACTUAL origin and exact recipe")
        if self.source_kind == "ACTUAL" and self.recipe_kind == "CUSTOM" and not self.actual_recipe_snapshot_id:
            raise ValueError("actual custom purchase requires per-line snapshot")
        if self.source_kind == "SYNTHETIC" and not self.source_system.startswith("research:"):
            raise ValueError("synthetic data requires a research source namespace")
        return self

    def payload_hash(self):
        return content_hash(self.model_dump())
