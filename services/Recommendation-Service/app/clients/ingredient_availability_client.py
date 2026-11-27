from collections import defaultdict
from contextlib import nullcontext
from decimal import Decimal
from uuid import UUID

import httpx
from pydantic import Field

from app.clients.catalog_client import CatalogClient
from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings
from app.schemas.catalog import IngredientResponse, UnitResponse
from app.service.bom_recommendation_service import InventoryIngredientPage, StockResponse
from app.service.level2_availability import AvailabilityUnavailable, _validate_inventory_origin


class RetailIngredient(IngredientResponse):
    retailPricePerUnit: Decimal | None = Field(default=None, allow_inf_nan=False)


def load_catalog_units(catalog: CatalogClient) -> dict[str, UnitResponse]:
    return {unit.id: unit for row in catalog.fetch_all_pages("/api/catalog/units", {"status": "ACTIVE"})
            if (unit := UnitResponse.model_validate(row)).status == "ACTIVE"}


def load_retail_catalog(catalog: CatalogClient, *, units: dict[str, UnitResponse] | None = None
                        ) -> dict[UUID, RetailIngredient]:
    # Catalog's ingredient listing excludes deleted records. Units are checked independently.
    ingredients = catalog.fetch_all_pages("/api/catalog/ingredients", {"status": "ACTIVE"})
    if units is None:
        units = load_catalog_units(catalog)
    result = {}
    seen = set()
    for row in ingredients:
        item = RetailIngredient.model_validate(row)
        ingredient_id = UUID(item.id)
        if ingredient_id in seen:
            raise ValueError("Duplicate Catalog ingredient")
        seen.add(ingredient_id)
        unit = units.get(item.unitId)
        if (item.status == "ACTIVE" and item.type == 1 and item.retailPricePerUnit is not None
                and item.retailPricePerUnit > 0 and item.ingredientCode.strip() and item.name.strip()
                and unit and unit.symbol.strip() and item.unitSymbol == unit.symbol):
            result[ingredient_id] = item
    return result


def _warehouse_units(http: httpx.Client, url, timeout: float) -> dict[UUID, list[UUID | None]]:
    units = defaultdict(list)
    for page in range(20):
        response = http.get(url, params={
            "itemType": "INGREDIENT", "status": "ACTIVE", "size": 100, "page": page, "sort": "id,asc",
        }, headers=generate_internal_auth_headers(), timeout=timeout)
        response.raise_for_status()
        batch = InventoryIngredientPage.model_validate_json(response.content)
        for item in batch.content:
            if item.status == "ACTIVE" and item.itemType == "INGREDIENT":
                units[item.referenceId].append(item.unitId)
        if batch.last:
            return dict(units)
    raise AvailabilityUnavailable("Incomplete ingredient stock mapping")


def warehouse_ingredient_units(settings: Settings, client: httpx.Client) -> dict[UUID, list[UUID | None]]:
    origin = _validate_inventory_origin(settings.level2_inventory_service_url)
    return _warehouse_units(client, origin.join("/api/inventory/inventory-items"), settings.level2_stock_timeout_seconds)


def warehouse_ingredient_stock(settings: Settings, candidates: dict[UUID, RetailIngredient],
                               branch_id: UUID | None, *, client: httpx.Client | None = None,
                               units: dict[UUID, list[UUID | None]] | None = None) -> dict[UUID, Decimal]:
    if not candidates:
        return {}
    origin = _validate_inventory_origin(settings.level2_inventory_service_url)
    timeout = settings.level2_stock_timeout_seconds
    transport = nullcontext(client) if client is not None else httpx.Client(
        base_url=origin, timeout=timeout, trust_env=False,
    )
    item_path = "/api/inventory/inventory-items"
    stock_path = "/internal/inventory/custom-bundles/availability"
    item_url = origin.join(item_path) if client is not None else item_path
    stock_url = origin.join(stock_path) if client is not None else stock_path
    with transport as http:
        if units is None:
            units = _warehouse_units(http, item_url, timeout)

        matched = [ingredient_id for ingredient_id, item in sorted(candidates.items())
                   if units.get(ingredient_id) == [UUID(item.unitId)]]
        available = {}
        for offset in range(0, len(matched), 50):
            ids = matched[offset:offset + 50]
            # A positive probe is required by the read-only warehouse API. Use its available quantity.
            probe = Decimal("0.001")
            response = http.post(stock_url, json={
                "branchId": str(branch_id) if branch_id else None,
                "lines": [{"ingredientId": str(i), "quantity": str(probe)} for i in ids],
            }, headers=generate_internal_auth_headers(), timeout=timeout)
            response.raise_for_status()
            batch = StockResponse.model_validate_json(response.content)
            if (len(batch.lines) != len(ids)
                    or {row.ingredientId for row in batch.lines} != set(ids)
                    or any(row.requiredQuantity != probe
                           or row.missingQuantity != max(Decimal(0), probe - row.availableQuantity)
                           for row in batch.lines)):
                raise AvailabilityUnavailable("Warehouse stock response mismatch")
            available.update({row.ingredientId: row.availableQuantity for row in batch.lines
                              if row.availableQuantity >= probe})
        return available
