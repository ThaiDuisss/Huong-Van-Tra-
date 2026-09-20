import httpx

from app.core.config import settings
from app.core.internal_auth import generate_internal_auth_headers
from app.schemas.catalog import (
    BomResponse,
    CategoryResponse,
    IngredientResponse,
    ProductResponse,
    ProductVariantResponse,
)


class CatalogClient:
    def __init__(self, base_url: str | None = None, timeout: float = 10.0) -> None:
        self.base_url = base_url or settings.catalog_service_url
        self.timeout = timeout
        self._client = httpx.Client(
            base_url=self.base_url,
            timeout=self.timeout,
        )

    def close(self) -> None:
        self._client.close()

    def __enter__(self) -> "CatalogClient":
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    def get_page(
        self,
        path: str,
        params: dict | None = None,
    ) -> dict:
        headers = generate_internal_auth_headers()
        response = self._client.get(
            path,
            params=params,
            headers=headers,
        )
        response.raise_for_status()
        return response.json()

    def fetch_all_pages(
        self,
        path: str,
        params: dict | None = None,
    ) -> list[dict]:
        page = 0
        size = 500
        results: list[dict] = []

        while True:
            query = {
                **(params or {}),
                "page": page,
                "size": size,
                "sort": "id,asc",
            }

            data = self.get_page(
                path=path,
                params=query,
            )

            if not isinstance(data, dict) or "content" not in data:
                raise ValueError(f"Malformed page response from {path}: missing 'content'")

            content = data["content"]
            results.extend(content)

            total_pages = data.get("totalPages", 1)
            is_last = data.get("last", True)

            if is_last or page >= total_pages - 1 or len(content) == 0:
                break

            page += 1

        return results

    def get_categories(self) -> list[CategoryResponse]:
        data = self.fetch_all_pages("/api/catalog/categories")
        return [CategoryResponse.model_validate(item) for item in data]

    def get_products(self) -> list[ProductResponse]:
        data = self.fetch_all_pages("/api/catalog/products")
        return [ProductResponse.model_validate(item) for item in data]

    def get_product_variants(self) -> list[ProductVariantResponse]:
        data = self.fetch_all_pages("/api/catalog/product-variants")
        return [ProductVariantResponse.model_validate(item) for item in data]

    def get_ingredients(self) -> list[IngredientResponse]:
        data = self.fetch_all_pages("/api/catalog/ingredients")
        return [IngredientResponse.model_validate(item) for item in data]

    def get_active_boms(self) -> list[BomResponse]:
        data = self.fetch_all_pages(
            "/api/catalog/boms",
            {"status": "ACTIVE"},
        )
        return [BomResponse.model_validate(item) for item in data]