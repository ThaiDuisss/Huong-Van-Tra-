from decimal import Decimal
import logging

import httpx
from sqlalchemy.orm import Session

from app.clients.catalog_client import CatalogClient
from app.core.config import get_settings
from app.models.product_variant import ProductVariant
from app.repositories.product_variant_repository import (
    ProductVariantRepository,
)
from app.schemas.catalog import ProductVariantResponse
from app.service.taste_catalog_sync_service import get_taste_catalog_sync

logger = logging.getLogger(__name__)


class ProductResolutionError(Exception):
    """Raised when an external product variant or SKU cannot be resolved to a local product_id."""


class ProductResolver:
    def __init__(
        self,
        variant_repository: ProductVariantRepository,
    ) -> None:
        self.variant_repository = variant_repository

    @staticmethod
    def _resolve_from_projection(
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> ProductVariant | None:
        try:
            view = get_taste_catalog_sync().read()
        except Exception:
            return None
        if view.payload is None:
            return None
        for row in view.payload.variants:
            if (product_variant_id and row.id == product_variant_id) or (
                sku and row.sku == sku
            ):
                return ProductVariant(
                    id=row.id,
                    product_id=row.productId,
                    sku=row.sku,
                    variant_name=getattr(row, "variantName", None) or getattr(row, "name", None) or row.sku,
                    selling_price=Decimal("0.00"),
                    status="ACTIVE" if row.sellable else "INACTIVE",
                    is_deleted=False,
                )
        return None

    @staticmethod
    def _resolve_from_catalog_api(
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> ProductVariant | None:
        if not product_variant_id and not sku:
            return None
        try:
            settings = get_settings()
            if not settings.catalog_service_url:
                return None
            with CatalogClient(
                settings.catalog_service_url,
                timeout=settings.level2_stock_timeout_seconds,
            ) as client:
                if product_variant_id:
                    data = client.get_page(f"/api/catalog/product-variants/{product_variant_id}")
                    parsed = ProductVariantResponse.model_validate(data)
                else:
                    matches = [
                        row for row in client.get_product_variants()
                        if row.sku == sku
                    ]
                    if not matches:
                        return None
                    parsed = matches[0]
            return ProductVariant(
                id=parsed.id,
                product_id=parsed.productId,
                sku=parsed.sku,
                variant_name=parsed.variantName,
                selling_price=parsed.sellingPrice,
                status=parsed.status,
                is_deleted=False,
            )
        except (httpx.HTTPError, ValueError, RuntimeError) as exc:
            logger.debug("Live Catalog variant lookup unavailable for variant=%s sku=%s: %s",
                         product_variant_id, sku, exc)
            return None

    def resolve_variant(
        self,
        db: Session,
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> ProductVariant | None:
        if product_variant_id:
            variant = self.variant_repository.find_by_id(db, product_variant_id)
            if variant is not None:
                return variant

        if sku:
            variant = self.variant_repository.find_by_sku(db, sku)
            if variant is not None:
                return variant

        projected = self._resolve_from_projection(
            product_variant_id=product_variant_id,
            sku=sku,
        )
        if projected is not None:
            return projected

        return self._resolve_from_catalog_api(
            product_variant_id=product_variant_id,
            sku=sku,
        )

    def resolve_product_id(
        self,
        db: Session,
        product_variant_id: str | None = None,
        sku: str | None = None,
    ) -> str | None:
        variant = self.resolve_variant(
            db,
            product_variant_id=product_variant_id,
            sku=sku,
        )
        if variant is None:
            return None
        return variant.product_id