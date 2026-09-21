from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.clients.catalog_client import CatalogClient
from app.database.session import get_db
from app.repositories.category_repository import CategoryRepository
from app.repositories.ingredient_repository import IngredientRepository
from app.repositories.product_ingredient_repository import ProductIngredientRepository
from app.repositories.product_repository import ProductRepository
from app.repositories.product_variant_repository import ProductVariantRepository
from app.service.catalog_sync_service import CatalogSyncService
from app.service.product_resolver import ProductResolver

router = APIRouter(
    prefix="/internal/catalog",
    tags=["catalog-sync"],
)


@router.post("/sync")
def sync_catalog(
    db: Session = Depends(get_db),
) -> dict:
    with CatalogClient() as catalog_client:
        category_repo = CategoryRepository()
        product_repo = ProductRepository()
        variant_repo = ProductVariantRepository()
        ingredient_repo = IngredientRepository()
        product_ingredient_repo = ProductIngredientRepository()
        resolver = ProductResolver(variant_repository=variant_repo)

        service = CatalogSyncService(
            catalog_client=catalog_client,
            category_repository=category_repo,
            product_repository=product_repo,
            variant_repository=variant_repo,
            ingredient_repository=ingredient_repo,
            product_ingredient_repository=product_ingredient_repo,
            product_resolver=resolver,
        )

        return service.sync_all(db)