from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
from decimal import Decimal
import json
from pathlib import Path
import sys
from typing import Any
from uuid import UUID, uuid5

from sqlalchemy import select
from sqlalchemy.orm import Session

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database.session import SessionLocal
from app.models.category import Category
from app.models.customer_interaction import CustomerInteraction
from app.models.ingredient import Ingredient
from app.models.interaction_event import InteractionEvent
from app.models.product import Product
from app.models.product_ingredient import ProductIngredient
from app.models.product_variant import ProductVariant


DEMO_NAMESPACE = UUID("718a2a8d-eec8-4d63-958a-d8c53ea612d2")
DEMO_VERSION = "hvtra-ai-demo-v1"
ACTIVE = "ACTIVE"
BASE_EVENT_TIME = datetime(2026, 1, 1, 8, 0, 0)


def demo_id(resource: str, key: str) -> str:
    return str(uuid5(DEMO_NAMESPACE, f"{DEMO_VERSION}:{resource}:{key}"))


@dataclass(frozen=True, slots=True)
class CategorySeed:
    code: str
    name: str


@dataclass(frozen=True, slots=True)
class IngredientSeed:
    code: str
    name: str


@dataclass(frozen=True, slots=True)
class ProductSeed:
    code: str
    name: str
    category_code: str
    price: Decimal
    ingredient_codes: tuple[str, ...]


CATEGORIES = (
    CategorySeed("DEMO_AI_GREEN", "Demo AI - Trà xanh"),
    CategorySeed("DEMO_AI_BLACK", "Demo AI - Trà đen"),
    CategorySeed("DEMO_AI_HERBAL", "Demo AI - Trà thảo mộc"),
    CategorySeed("DEMO_AI_OOLONG", "Demo AI - Trà ô long"),
)

INGREDIENTS = (
    IngredientSeed("DEMO_AI_GREEN_TEA", "Lá trà xanh"),
    IngredientSeed("DEMO_AI_BLACK_TEA", "Lá trà đen"),
    IngredientSeed("DEMO_AI_OOLONG_TEA", "Lá trà ô long"),
    IngredientSeed("DEMO_AI_JASMINE", "Hoa nhài"),
    IngredientSeed("DEMO_AI_LOTUS", "Hoa sen"),
    IngredientSeed("DEMO_AI_MATCHA", "Matcha"),
    IngredientSeed("DEMO_AI_MINT", "Bạc hà"),
    IngredientSeed("DEMO_AI_GINGER", "Gừng"),
    IngredientSeed("DEMO_AI_BERGAMOT", "Cam bergamot"),
    IngredientSeed("DEMO_AI_CINNAMON", "Quế"),
    IngredientSeed("DEMO_AI_PEACH", "Đào"),
    IngredientSeed("DEMO_AI_ROSE", "Hoa hồng"),
    IngredientSeed("DEMO_AI_CHAMOMILE", "Cúc La Mã"),
    IngredientSeed("DEMO_AI_LEMONGRASS", "Sả"),
    IngredientSeed("DEMO_AI_HIBISCUS", "Atiso đỏ"),
    IngredientSeed("DEMO_AI_CITRUS", "Vỏ cam"),
)

PRODUCTS = (
    ProductSeed("DEMO-AI-GREEN-JASMINE", "Trà Xanh Nhài", "DEMO_AI_GREEN", Decimal("89000"), ("DEMO_AI_GREEN_TEA", "DEMO_AI_JASMINE")),
    ProductSeed("DEMO-AI-GREEN-LOTUS", "Trà Xanh Sen", "DEMO_AI_GREEN", Decimal("95000"), ("DEMO_AI_GREEN_TEA", "DEMO_AI_LOTUS")),
    ProductSeed("DEMO-AI-GREEN-MATCHA", "Trà Xanh Matcha", "DEMO_AI_GREEN", Decimal("105000"), ("DEMO_AI_GREEN_TEA", "DEMO_AI_MATCHA")),
    ProductSeed("DEMO-AI-GREEN-MINT", "Trà Xanh Bạc Hà", "DEMO_AI_GREEN", Decimal("92000"), ("DEMO_AI_GREEN_TEA", "DEMO_AI_MINT")),
    ProductSeed("DEMO-AI-GREEN-GINGER", "Trà Xanh Gừng", "DEMO_AI_GREEN", Decimal("90000"), ("DEMO_AI_GREEN_TEA", "DEMO_AI_GINGER")),
    ProductSeed("DEMO-AI-BLACK-BERGAMOT", "Trà Đen Bergamot", "DEMO_AI_BLACK", Decimal("99000"), ("DEMO_AI_BLACK_TEA", "DEMO_AI_BERGAMOT")),
    ProductSeed("DEMO-AI-BLACK-CINNAMON", "Trà Đen Quế", "DEMO_AI_BLACK", Decimal("97000"), ("DEMO_AI_BLACK_TEA", "DEMO_AI_CINNAMON")),
    ProductSeed("DEMO-AI-BLACK-GINGER", "Trà Đen Gừng", "DEMO_AI_BLACK", Decimal("96000"), ("DEMO_AI_BLACK_TEA", "DEMO_AI_GINGER")),
    ProductSeed("DEMO-AI-BLACK-PEACH", "Trà Đen Đào", "DEMO_AI_BLACK", Decimal("102000"), ("DEMO_AI_BLACK_TEA", "DEMO_AI_PEACH")),
    ProductSeed("DEMO-AI-BLACK-ROSE", "Trà Đen Hoa Hồng", "DEMO_AI_BLACK", Decimal("104000"), ("DEMO_AI_BLACK_TEA", "DEMO_AI_ROSE")),
    ProductSeed("DEMO-AI-HERBAL-CHAMOMILE", "Trà Cúc Dịu Êm", "DEMO_AI_HERBAL", Decimal("85000"), ("DEMO_AI_CHAMOMILE", "DEMO_AI_LEMONGRASS")),
    ProductSeed("DEMO-AI-HERBAL-MINT", "Trà Thảo Mộc Bạc Hà", "DEMO_AI_HERBAL", Decimal("82000"), ("DEMO_AI_LEMONGRASS", "DEMO_AI_MINT")),
    ProductSeed("DEMO-AI-HERBAL-GINGER", "Trà Thảo Mộc Gừng", "DEMO_AI_HERBAL", Decimal("84000"), ("DEMO_AI_LEMONGRASS", "DEMO_AI_GINGER")),
    ProductSeed("DEMO-AI-HERBAL-ROSE", "Trà Hoa Hồng Atiso", "DEMO_AI_HERBAL", Decimal("93000"), ("DEMO_AI_ROSE", "DEMO_AI_HIBISCUS")),
    ProductSeed("DEMO-AI-HERBAL-CITRUS", "Trà Thảo Mộc Cam Sả", "DEMO_AI_HERBAL", Decimal("88000"), ("DEMO_AI_LEMONGRASS", "DEMO_AI_CITRUS")),
    ProductSeed("DEMO-AI-OOLONG-PEACH", "Ô Long Đào", "DEMO_AI_OOLONG", Decimal("112000"), ("DEMO_AI_OOLONG_TEA", "DEMO_AI_PEACH")),
    ProductSeed("DEMO-AI-OOLONG-LOTUS", "Ô Long Sen", "DEMO_AI_OOLONG", Decimal("115000"), ("DEMO_AI_OOLONG_TEA", "DEMO_AI_LOTUS")),
    ProductSeed("DEMO-AI-OOLONG-ROSE", "Ô Long Hoa Hồng", "DEMO_AI_OOLONG", Decimal("118000"), ("DEMO_AI_OOLONG_TEA", "DEMO_AI_ROSE")),
    ProductSeed("DEMO-AI-OOLONG-CITRUS", "Ô Long Cam", "DEMO_AI_OOLONG", Decimal("110000"), ("DEMO_AI_OOLONG_TEA", "DEMO_AI_CITRUS")),
    ProductSeed("DEMO-AI-OOLONG-JASMINE", "Ô Long Nhài", "DEMO_AI_OOLONG", Decimal("116000"), ("DEMO_AI_OOLONG_TEA", "DEMO_AI_JASMINE")),
)

# Four category-oriented cohorts and one aroma-oriented cross-category cohort.
# Each customer buys four of five cohort products. Peers therefore provide a
# purposeful unseen product for collaborative ranking instead of random noise.
PREFERENCE_PROFILES = (
    (0, 1, 2, 3, 4),
    (5, 6, 7, 8, 9),
    (10, 11, 12, 13, 14),
    (15, 16, 17, 18, 19),
    (0, 5, 10, 15, 17),
)
PURCHASE_FREQUENCIES = (5, 4, 3, 2)


def _validate_fields(record: Any, expected: dict[str, Any], label: str) -> None:
    mismatches = {
        field: (getattr(record, field), value)
        for field, value in expected.items()
        if getattr(record, field) != value
    }
    if mismatches:
        raise RuntimeError(f"Existing {label} does not match demo contract: {mismatches}")


def _ensure_category(db: Session, seed: CategorySeed) -> bool:
    identifier = demo_id("category", seed.code)
    records = db.scalars(
        select(Category).where(
            (Category.id == identifier) | (Category.category_code == seed.code)
        )
    ).all()
    if records:
        if len(records) != 1 or records[0].id != identifier:
            raise RuntimeError(f"Category identity collision for {seed.code}")
        _validate_fields(
            records[0],
            {
                "category_code": seed.code,
                "name": seed.name,
                "parent_id": None,
                "status": ACTIVE,
                "is_deleted": False,
            },
            f"category {seed.code}",
        )
        return False
    db.add(
        Category(
            id=identifier,
            category_code=seed.code,
            name=seed.name,
            parent_id=None,
            status=ACTIVE,
            is_deleted=False,
        )
    )
    return True


def _ensure_ingredient(db: Session, seed: IngredientSeed) -> bool:
    identifier = demo_id("ingredient", seed.code)
    records = db.scalars(
        select(Ingredient).where(
            (Ingredient.id == identifier)
            | (Ingredient.ingredient_code == seed.code)
        )
    ).all()
    if records:
        if len(records) != 1 or records[0].id != identifier:
            raise RuntimeError(f"Ingredient identity collision for {seed.code}")
        _validate_fields(
            records[0],
            {
                "ingredient_code": seed.code,
                "name": seed.name,
                "status": ACTIVE,
                "is_deleted": False,
            },
            f"ingredient {seed.code}",
        )
        return False
    db.add(
        Ingredient(
            id=identifier,
            ingredient_code=seed.code,
            name=seed.name,
            status=ACTIVE,
            type=1,
            is_deleted=False,
        )
    )
    return True


def _ensure_product(db: Session, seed: ProductSeed) -> tuple[bool, bool]:
    product_id = demo_id("product", seed.code)
    category_id = demo_id("category", seed.category_code)
    records = db.scalars(
        select(Product).where(
            (Product.id == product_id) | (Product.product_code == seed.code)
        )
    ).all()
    product_inserted = False
    if records:
        if len(records) != 1 or records[0].id != product_id:
            raise RuntimeError(f"Product identity collision for {seed.code}")
        _validate_fields(
            records[0],
            {
                "category_id": category_id,
                "product_code": seed.code,
                "name": seed.name,
                "status": ACTIVE,
                "is_deleted": False,
            },
            f"product {seed.code}",
        )
    else:
        db.add(
            Product(
                id=product_id,
                category_id=category_id,
                product_code=seed.code,
                name=seed.name,
                status=ACTIVE,
                is_deleted=False,
            )
        )
        product_inserted = True

    variant_id = demo_id("variant", seed.code)
    sku = f"{seed.code}-100G"
    variants = db.scalars(
        select(ProductVariant).where(
            (ProductVariant.id == variant_id) | (ProductVariant.sku == sku)
        )
    ).all()
    variant_inserted = False
    if variants:
        if len(variants) != 1 or variants[0].id != variant_id:
            raise RuntimeError(f"Variant identity collision for {sku}")
        _validate_fields(
            variants[0],
            {
                "product_id": product_id,
                "sku": sku,
                "variant_name": "Gói 100g",
                "selling_price": seed.price,
                "status": ACTIVE,
                "is_deleted": False,
            },
            f"variant {sku}",
        )
    else:
        db.add(
            ProductVariant(
                id=variant_id,
                product_id=product_id,
                sku=sku,
                variant_name="Gói 100g",
                selling_price=seed.price,
                status=ACTIVE,
                is_deleted=False,
            )
        )
        variant_inserted = True
    return product_inserted, variant_inserted


def _ensure_product_ingredient(
    db: Session,
    product: ProductSeed,
    ingredient_code: str,
) -> bool:
    identifier = demo_id("product-ingredient", f"{product.code}:{ingredient_code}")
    product_id = demo_id("product", product.code)
    ingredient_id = demo_id("ingredient", ingredient_code)
    variant_id = demo_id("variant", product.code)
    records = db.scalars(
        select(ProductIngredient).where(
            (ProductIngredient.id == identifier)
            | (
                (ProductIngredient.product_id == product_id)
                & (ProductIngredient.ingredient_id == ingredient_id)
                & (ProductIngredient.source_variant_id == variant_id)
                & (ProductIngredient.bom_version == 1)
            )
        )
    ).all()
    if records:
        if len(records) != 1 or records[0].id != identifier:
            raise RuntimeError(
                f"Product ingredient identity collision for {product.code}/{ingredient_code}"
            )
        _validate_fields(
            records[0],
            {
                "product_id": product_id,
                "ingredient_id": ingredient_id,
                "ingredient_code": ingredient_code,
                "source_variant_id": variant_id,
                "bom_version": 1,
                "is_active_bom": True,
            },
            f"product ingredient {product.code}/{ingredient_code}",
        )
        return False
    db.add(
        ProductIngredient(
            id=identifier,
            product_id=product_id,
            ingredient_id=ingredient_id,
            ingredient_code=ingredient_code,
            source_variant_id=variant_id,
            bom_version=1,
            is_active_bom=True,
        )
    )
    return True


def _build_interactions() -> list[dict[str, Any]]:
    interactions: list[dict[str, Any]] = []
    for customer_offset in range(30):
        customer_number = customer_offset + 1
        customer_id = demo_id("customer", f"{customer_number:02d}")
        profile = PREFERENCE_PROFILES[customer_offset // 6]
        omitted_position = customer_offset % len(profile)
        purchased_product_indexes = [
            product_index
            for position, product_index in enumerate(profile)
            if position != omitted_position
        ]
        event_sequence = 0
        for product_index, frequency in zip(
            purchased_product_indexes,
            PURCHASE_FREQUENCIES,
        ):
            product = PRODUCTS[product_index]
            for repetition in range(frequency):
                event_sequence += 1
                event_key = f"c{customer_number:02d}:e{event_sequence:02d}"
                event_time = (
                    BASE_EVENT_TIME
                    + timedelta(days=customer_offset * 2, hours=event_sequence)
                )
                interactions.append(
                    {
                        "id": demo_id("interaction", event_key),
                        "source_event_id": demo_id("source-event", event_key),
                        "order_item_id": demo_id("order-item", event_key),
                        "return_item_id": None,
                        "customer_id": customer_id,
                        "product_variant_id": demo_id("variant", product.code),
                        "product_id": demo_id("product", product.code),
                        "event_type": "PURCHASE",
                        "quantity": Decimal("2.000") if repetition == 0 else Decimal("1.000"),
                        "unit_price": product.price,
                        "order_id": demo_id("order", event_key),
                        "return_id": None,
                        "return_reason": None,
                        "event_time": event_time,
                    }
                )
    return interactions


def _ensure_interactions(
    db: Session,
    expected: list[dict[str, Any]],
) -> tuple[int, int]:
    customer_ids = {row["customer_id"] for row in expected}
    source_event_ids = {row["source_event_id"] for row in expected}
    unexpected_count = len(
        db.scalars(
            select(InteractionEvent.id).where(
                InteractionEvent.customer_id.in_(customer_ids),
                InteractionEvent.source_event_id.not_in(source_event_ids),
            )
        ).all()
    )
    if unexpected_count:
        raise RuntimeError(
            "Dedicated demo customer IDs already own non-demo interaction events"
        )

    existing_by_source = {
        row.source_event_id: row
        for row in db.scalars(
            select(InteractionEvent).where(
                InteractionEvent.source_event_id.in_(source_event_ids)
            )
        )
    }
    expected_ids = {row["id"] for row in expected}
    existing_by_id = {
        row.id: row
        for row in db.scalars(
            select(InteractionEvent).where(InteractionEvent.id.in_(expected_ids))
        )
    }

    inserted = 0
    reused = 0
    for values in expected:
        existing = existing_by_source.get(values["source_event_id"])
        id_owner = existing_by_id.get(values["id"])
        if existing is not None or id_owner is not None:
            if existing is None or id_owner is None or existing.id != id_owner.id:
                raise RuntimeError(
                    f"Interaction identity collision for {values['source_event_id']}"
                )
            _validate_fields(
                existing,
                {key: value for key, value in values.items() if key != "id"},
                f"interaction {values['source_event_id']}",
            )
            reused += 1
            continue
        db.add(InteractionEvent(**values))
        inserted += 1
    return inserted, reused


def _ensure_aggregates(
    db: Session,
    interactions: list[dict[str, Any]],
) -> tuple[int, int]:
    aggregates: dict[tuple[str, str], dict[str, Any]] = defaultdict(
        lambda: {
            "total_purchase_count": 0,
            "total_purchase_quantity": Decimal("0.000"),
            "total_return_count": 0,
            "total_return_quantity": Decimal("0.000"),
            "last_interaction_time": BASE_EVENT_TIME,
        }
    )
    for interaction in interactions:
        key = (interaction["customer_id"], interaction["product_id"])
        aggregate = aggregates[key]
        aggregate["total_purchase_count"] += 1
        aggregate["total_purchase_quantity"] += interaction["quantity"]
        aggregate["last_interaction_time"] = max(
            aggregate["last_interaction_time"],
            interaction["event_time"],
        )

    customer_ids = {customer_id for customer_id, _ in aggregates}
    existing_rows = db.scalars(
        select(CustomerInteraction).where(
            CustomerInteraction.customer_id.in_(customer_ids)
        )
    ).all()
    expected_keys = set(aggregates)
    if any(
        (row.customer_id, row.product_id) not in expected_keys
        for row in existing_rows
    ):
        raise RuntimeError(
            "Dedicated demo customer IDs already own non-demo aggregate rows"
        )
    existing_by_key = {
        (row.customer_id, row.product_id): row for row in existing_rows
    }

    inserted = 0
    reused = 0
    for (customer_id, product_id), values in sorted(aggregates.items()):
        existing = existing_by_key.get((customer_id, product_id))
        if existing is not None:
            _validate_fields(
                existing,
                values,
                f"customer aggregate {customer_id}/{product_id}",
            )
            reused += 1
            continue
        db.add(
            CustomerInteraction(
                customer_id=customer_id,
                product_id=product_id,
                **values,
            )
        )
        inserted += 1
    return inserted, reused


def seed() -> dict[str, Any]:
    counters = defaultdict(int)
    interactions = _build_interactions()
    if len(interactions) != 420:
        raise RuntimeError("Demo interaction contract must contain exactly 420 events")

    with SessionLocal() as db:
        try:
            for category in CATEGORIES:
                counters["categoriesInserted"] += int(_ensure_category(db, category))
            db.flush()

            for ingredient in INGREDIENTS:
                counters["ingredientsInserted"] += int(_ensure_ingredient(db, ingredient))
            db.flush()

            for product in PRODUCTS:
                product_inserted, variant_inserted = _ensure_product(db, product)
                counters["productsInserted"] += int(product_inserted)
                counters["variantsInserted"] += int(variant_inserted)
            db.flush()

            for product in PRODUCTS:
                for ingredient_code in product.ingredient_codes:
                    counters["productIngredientsInserted"] += int(
                        _ensure_product_ingredient(db, product, ingredient_code)
                    )
            db.flush()

            inserted, reused = _ensure_interactions(db, interactions)
            counters["interactionEventsInserted"] = inserted
            counters["interactionEventsReused"] = reused
            db.flush()

            inserted, reused = _ensure_aggregates(db, interactions)
            counters["customerInteractionsInserted"] = inserted
            counters["customerInteractionsReused"] = reused
            db.commit()
        except Exception:
            db.rollback()
            raise

    return {
        "demoVersion": DEMO_VERSION,
        "customerCount": 30,
        "productCount": 20,
        "purchaseEventCount": len(interactions),
        "positiveCustomerProductPairs": 120,
        "sampleCustomerId": demo_id("customer", "01"),
        **dict(sorted(counters.items())),
    }


if __name__ == "__main__":
    print(json.dumps(seed(), indent=2, sort_keys=True))
