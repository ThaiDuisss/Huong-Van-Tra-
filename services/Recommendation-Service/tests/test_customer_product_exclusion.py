from decimal import Decimal
import unittest
from unittest.mock import MagicMock, patch

from app.core.settings import Settings
from app.schemas.catalog import CategoryResponse, ProductResponse, ProductVariantResponse
from app.service.catalog_sync_service import CatalogSyncService
from app.service.level2_availability import Level2Availability


def _category(code: str) -> CategoryResponse:
    return CategoryResponse(
        id="11111111-1111-4111-8111-111111111111",
        name=code,
        status="ACTIVE",
        categoryCode=code,
    )


def _variant(vid: str, pid: str, sku: str, product_type: str = "FINISHED_PRODUCT") -> ProductVariantResponse:
    return ProductVariantResponse(
        id=vid,
        productId=pid,
        sku=sku,
        variantName=sku,
        sellingPrice=Decimal("90000"),
        status="ACTIVE",
        productStatus="ACTIVE",
        sellable=True,
        productType=product_type,
    )


class CustomerProductExclusionTests(unittest.TestCase):
    def test_catalog_sync_excludes_customer_products_and_variants(self):
        catalog_client = MagicMock()
        prod_repo = MagicMock()
        var_repo = MagicMock()
        bom_repo = MagicMock()
        db = MagicMock()

        normal_product = ProductResponse(
            id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
            category=_category("TRA-XANH"),
            productCode="TX-01",
            name="Trà Xanh",
            status="ACTIVE",
            variants=[
                _variant(
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa11",
                    "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
                    "TX-001",
                    "FINISHED_PRODUCT",
                )
            ],
        )
        customer_product = ProductResponse(
            id="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2",
            category=_category("CUSTOMER-BUNDLE"),
            productCode="KH-01",
            name="Trà Khách Tự Chọn",
            status="ACTIVE",
            variants=[
                _variant(
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb22",
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2",
                    "KH-001",
                    "CUSTOMER",
                )
            ],
        )
        catalog_client.get_products.return_value = [normal_product, customer_product]
        catalog_client.get_product_variants.return_value = [
            normal_product.variants[0],
            customer_product.variants[0],
        ]

        sync = CatalogSyncService(
            catalog_client=catalog_client,
            category_repository=MagicMock(),
            product_repository=prod_repo,
            variant_repository=var_repo,
            ingredient_repository=MagicMock(),
            product_ingredient_repository=bom_repo,
        )

        self.assertEqual(sync.sync_products(db), 1)
        self.assertEqual(sync.sync_variants(db), 1)
        self.assertEqual(prod_repo.upsert.call_args.args[1].id, normal_product.id)
        self.assertEqual(var_repo.upsert.call_args.args[1].id, normal_product.variants[0].id)

    def test_level2_availability_excludes_customer_variants(self):
        customer_v = _variant(
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb22",
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2",
            "KH-001",
            "CUSTOMER",
        )
        normal_v = _variant(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa11",
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1",
            "TX-001",
            "FINISHED_PRODUCT",
        )

        fake_catalog = MagicMock()
        fake_catalog.__enter__.return_value = fake_catalog
        fake_catalog.get_product_variants.return_value = [customer_v, normal_v]
        captured = {}

        def fake_stock(candidates, settings):
            captured["ids"] = set(candidates)
            return set(candidates)

        availability = Level2Availability(Settings(
            catalog_service_url="http://localhost:8081",
            level2_inventory_service_url="http://localhost:8084",
            internal_header_secret="test-secret",
        ))
        with patch("app.service.level2_availability.CatalogClient", return_value=fake_catalog), \
             patch("app.service.level2_availability.fetch_available_variant_ids", side_effect=fake_stock):
            result = availability.available_variants({
                customer_v.id: customer_v.productId,
                normal_v.id: normal_v.productId,
            })

        self.assertEqual(captured["ids"], {normal_v.id})
        self.assertEqual(result, {normal_v.id: normal_v.productId})


if __name__ == "__main__":
    unittest.main()
