"""Level 2 uses live Catalog sellability and the same single-store stock read as POS."""
from collections import defaultdict
from decimal import Decimal
from uuid import UUID

import httpx
from pydantic import BaseModel, Field, TypeAdapter

from app.clients.catalog_client import CatalogClient
from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings


class AvailabilityUnavailable(RuntimeError):
    pass


class StockRow(BaseModel):
    variantId: UUID
    availableQuantity: Decimal = Field(ge=0, allow_inf_nan=False)


_stock_rows = TypeAdapter(list[StockRow])


def variants_by_product(variants: dict[str, str]) -> dict[str, set[str]]:
    result = defaultdict(set)
    for variant_id, product_id in variants.items():
        result[product_id].add(variant_id)
    return dict(result)


class Level2Availability:
    def __init__(self, settings: Settings):
        self.settings = settings

    def available_variants(self, mirror: dict[str, str]) -> dict[str, str]:
        if not mirror:
            return {}
        if not self.settings.level2_inventory_service_url:
            raise AvailabilityUnavailable("INVENTORY_NOT_CONFIGURED")
        try:
            origin = httpx.URL(self.settings.level2_inventory_service_url)
            if (origin.scheme not in {"http", "https"} or not origin.host or origin.userinfo
                    or origin.path != "/" or origin.query or origin.fragment):
                raise ValueError("Invalid Inventory origin")
            timeout = self.settings.level2_stock_timeout_seconds
            with CatalogClient(self.settings.catalog_service_url, timeout=timeout) as catalog:
                variants = catalog.get_product_variants(sellable_only=True)
            candidates = {}
            seen = set()
            for row in variants:
                if row.id not in mirror:
                    continue
                if row.id in seen or row.productId != mirror[row.id]:
                    raise ValueError("Catalog Variant identity conflict")
                seen.add(row.id)
                if (row.sellable is True and row.status == "ACTIVE" and row.sku != "CUSTOM-BUNDLE"
                        and row.sellingPrice.is_finite() and row.sellingPrice >= 0):
                    candidates[row.id] = row.productId
            ids = sorted(candidates)
            available = {}
            # Use aggregate saleable shelf + warehouse stock. Omit the optional location.
            with httpx.Client(base_url=origin, timeout=timeout, trust_env=False) as client:
                for offset in range(0, len(ids), 100):
                    batch = ids[offset:offset + 100]
                    response = client.post("/internal/inventory/stocks/variants/batch",
                                           json={"variantIds": batch}, headers=generate_internal_auth_headers())
                    response.raise_for_status()
                    stocks = _stock_rows.validate_json(response.content)
                    if len(stocks) != len(batch) or {str(row.variantId) for row in stocks} != set(batch):
                        raise ValueError("Incomplete or duplicate Inventory result")
                    available.update((str(row.variantId), candidates[str(row.variantId)])
                                     for row in stocks if row.availableQuantity > 0)
            return available
        except (ValueError, httpx.HTTPError) as error:
            raise AvailabilityUnavailable("AVAILABILITY_UNAVAILABLE") from error
