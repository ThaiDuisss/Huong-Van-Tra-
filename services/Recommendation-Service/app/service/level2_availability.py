"""Level 2 uses live Catalog sellability and the same single-store stock read as POS."""
from collections import defaultdict
from collections.abc import Iterable
from concurrent.futures import Executor
from contextlib import nullcontext
from contextvars import copy_context
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


def _validate_inventory_origin(url_value: str | None) -> httpx.URL:
    if not url_value:
        raise AvailabilityUnavailable("INVENTORY_NOT_CONFIGURED")
    try:
        origin = httpx.URL(url_value)
    except ValueError as error:
        raise AvailabilityUnavailable("AVAILABILITY_UNAVAILABLE") from error
    if (origin.scheme not in {"http", "https"} or not origin.host or origin.userinfo
            or origin.path != "/" or origin.query or origin.fragment):
        raise AvailabilityUnavailable("AVAILABILITY_UNAVAILABLE")
    return origin


def fetch_available_variant_ids(variant_ids: Iterable[str], settings: Settings, *,
                                client: httpx.Client | None = None, executor: Executor | None = None) -> set[str]:
    """Batch-lookup live Inventory stock (shelf + warehouse - reserved) and fail closed on any error."""
    ids = sorted(set(variant_ids))
    if not ids:
        return set()
    origin = _validate_inventory_origin(settings.level2_inventory_service_url)
    timeout = settings.level2_stock_timeout_seconds
    try:
        # Use aggregate saleable shelf + warehouse stock. Omit the optional location.
        transport = nullcontext(client) if client is not None else httpx.Client(
            base_url=origin, timeout=timeout, trust_env=False,
        )
        path = "/internal/inventory/stocks/variants/batch"
        url = origin.join(path) if client is not None else path
        with transport as http:
            def read(batch):
                response = http.post(url, json={"variantIds": batch}, headers=generate_internal_auth_headers(),
                                     timeout=timeout)
                response.raise_for_status()
                stocks = _stock_rows.validate_json(response.content)
                if len(stocks) != len(batch) or {str(row.variantId) for row in stocks} != set(batch):
                    raise ValueError("Incomplete or duplicate Inventory result")
                return {str(row.variantId) for row in stocks if row.availableQuantity > 0}

            batches = [ids[offset:offset + 100] for offset in range(0, len(ids), 100)]
            if executor is None or len(batches) == 1:
                return set().union(*(read(batch) for batch in batches))
            futures = [executor.submit(copy_context().run, read, batch) for batch in batches]
            return set().union(*(future.result() for future in futures))
    except (ValueError, httpx.HTTPError) as error:
        raise AvailabilityUnavailable("AVAILABILITY_UNAVAILABLE") from error


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
        _validate_inventory_origin(self.settings.level2_inventory_service_url)
        try:
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
                        and (row.productType or "").upper() != "CUSTOMER"
                        and row.sellingPrice.is_finite() and row.sellingPrice >= 0):
                    candidates[row.id] = row.productId
        except (ValueError, httpx.HTTPError) as error:
            raise AvailabilityUnavailable("AVAILABILITY_UNAVAILABLE") from error
        available_ids = fetch_available_variant_ids(candidates, self.settings)
        return {variant_id: candidates[variant_id] for variant_id in available_ids}
