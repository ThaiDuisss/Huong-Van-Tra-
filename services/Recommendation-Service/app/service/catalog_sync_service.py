import logging
import uuid
from sqlalchemy.orm import Session

from app.clients.catalog_client import CatalogClient
from app.models.category import Category
from app.models.ingredient import Ingredient
from app.models.product import Product
from app.models.product_ingredient import ProductIngredient
from app.models.product_variant import ProductVariant
from app.repositories.category_repository import CategoryRepository
from app.repositories.ingredient_repository import IngredientRepository
from app.repositories.product_ingredient_repository import ProductIngredientRepository
from app.repositories.product_repository import ProductRepository
from app.repositories.product_variant_repository import ProductVariantRepository
from app.service.product_resolver import ProductResolver

logger = logging.getLogger(__name__)


class CatalogSyncService:
    """
    Orchestrates initial catalog data synchronization from Catalog-Service to recommendation_db.

    KNOWN LIMITATION (V1):
    Catalog-Service REST APIs strictly filter out soft-deleted records (isDeleted=false).
    During subsequent manual re-syncs, records that were previously synced but subsequently
    soft-deleted in Catalog-Service will not be reconciled/tombstoned in recommendation_db.
    """

    def __init__(
        self,
        catalog_client: CatalogClient,
        category_repository: CategoryRepository,
        product_repository: ProductRepository,
        variant_repository: ProductVariantRepository,
        ingredient_repository: IngredientRepository,
        product_ingredient_repository: ProductIngredientRepository,
        product_resolver: ProductResolver,
    ) -> None:
        self.catalog_client = catalog_client
        self.category_repository = category_repository
        self.product_repository = product_repository
        self.variant_repository = variant_repository
        self.ingredient_repository = ingredient_repository
        self.product_ingredient_repository = product_ingredient_repository
        self.product_resolver = product_resolver

    def sync_categories(
        self,
        db: Session,
    ) -> int:
        categories = self.catalog_client.get_categories()
        for item in categories:
            category = Category(
                id=item.id,
                category_code=item.categoryCode,
                name=item.name,
                parent_id=item.parentId,
                status=item.status,
                is_deleted=False,
            )
            self.category_repository.upsert(db, category)

        db.commit()
        return len(categories)

    def sync_products(
        self,
        db: Session,
    ) -> int:
        products = self.catalog_client.get_products()
        for item in products:
            product = Product(
                id=item.id,
                category_id=item.category.id,
                product_code=item.productCode,
                name=item.name,
                status=item.status,
                is_deleted=False,
            )
            self.product_repository.upsert(db, product)

        db.commit()
        return len(products)

    def sync_variants(
        self,
        db: Session,
    ) -> int:
        variants = self.catalog_client.get_product_variants()
        for item in variants:
            variant = ProductVariant(
                id=item.id,
                product_id=item.productId,
                sku=item.sku,
                variant_name=item.variantName,
                selling_price=item.sellingPrice,
                status=item.status,
                is_deleted=False,
            )
            self.variant_repository.upsert(db, variant)

        db.commit()
        return len(variants)

    def sync_ingredients(
        self,
        db: Session,
    ) -> int:
        ingredients = self.catalog_client.get_ingredients()
        for item in ingredients:
            ingredient = Ingredient(
                id=item.id,
                ingredient_code=item.ingredientCode,
                name=item.name,
                status=item.status,
                is_deleted=False,
            )
            self.ingredient_repository.upsert(db, ingredient)

        db.commit()
        return len(ingredients)

    def sync_active_boms(
        self,
        db: Session,
    ) -> tuple[int, int]:
        boms = self.catalog_client.get_active_boms()
        active_bom_count = 0
        product_ingredient_count = 0

        for bom in boms:
            product_id = self.product_resolver.resolve_product_id(
                db,
                bom.productVariantId,
            )

            if product_id is None:
                logger.warning(
                    "Skipping BOM %s: Variant %s not found in local product_variants",
                    bom.id,
                    bom.productVariantId,
                )
                continue

            active_bom_count += 1

            # Deactivate older versions of the same variant before inserting current active version
            self.product_ingredient_repository.deactivate_old_versions(
                db,
                bom.productVariantId,
                bom.version,
            )

            for item in bom.items:
                product_ingredient = ProductIngredient(
                    id=str(uuid.uuid4()),
                    product_id=product_id,
                    ingredient_id=item.ingredient.id,
                    ingredient_code=item.ingredient.ingredientCode,
                    source_variant_id=bom.productVariantId,
                    bom_version=bom.version,
                    is_active_bom=(bom.status == "ACTIVE"),
                )

                self.product_ingredient_repository.upsert(
                    db,
                    product_ingredient,
                )
                product_ingredient_count += 1

        db.commit()
        return active_bom_count, product_ingredient_count

    def sync_all(
        self,
        db: Session,
    ) -> dict:
        try:
            category_count = self.sync_categories(db)
        except Exception:
            db.rollback()
            raise

        try:
            product_count = self.sync_products(db)
        except Exception:
            db.rollback()
            raise

        try:
            variant_count = self.sync_variants(db)
        except Exception:
            db.rollback()
            raise

        try:
            ingredient_count = self.sync_ingredients(db)
        except Exception:
            db.rollback()
            raise

        try:
            active_bom_count, product_ingredient_count = self.sync_active_boms(db)
        except Exception:
            db.rollback()
            raise

        return {
            "categories": category_count,
            "products": product_count,
            "variants": variant_count,
            "ingredients": ingredient_count,
            "activeBoms": active_bom_count,
            "productIngredients": product_ingredient_count,
        }
