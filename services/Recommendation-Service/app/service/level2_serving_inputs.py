from concurrent.futures import ThreadPoolExecutor
from contextvars import copy_context
from datetime import datetime, timezone
from functools import lru_cache
import logging
from uuid import UUID

import httpx
from sqlalchemy import func, select

from app.clients.bom_catalog_client import fetch_bom_recipes
from app.clients.catalog_client import CatalogClient
from app.clients.customer_taste_client import CustomerTasteClient
from app.clients.ingredient_availability_client import (
    load_catalog_units,
    load_retail_catalog,
    warehouse_ingredient_stock,
    warehouse_ingredient_units,
)
from app.core.config import get_settings
from app.core.settings import Settings
from app.database.session import SessionLocal
from app.models.customer_interaction import CustomerInteraction
from app.repositories.customer_order_evidence import load_order_evidence
from app.repositories.ingredient_history_repository import retained_from_evidence
from app.repositories.v1_recommendation_repository import V1RecommendationRepository
from app.service.level2_availability import fetch_available_variant_ids
from app.service.recommendation_cache import RecommendationCache
from app.service.taste_catalog_sync_service import get_taste_catalog_sync

logger = logging.getLogger(__name__)


class Level2ServingInputs:
    """Process-local bounded TTL caches for Level 2 serving inputs."""

    def __init__(self, settings: Settings):
        self.settings = settings
        history_ttl = settings.level2_history_cache_seconds
        catalog_ttl = settings.level2_catalog_cache_seconds
        stock_ttl = settings.level2_stock_cache_seconds
        capacity = settings.level2_serving_cache_capacity
        self._evidence = RecommendationCache(history_ttl, capacity)
        self._products = RecommendationCache(history_ttl, capacity)
        self._ingredients = RecommendationCache(history_ttl, capacity)
        self._surveys = RecommendationCache(max(history_ttl, 10), capacity)
        self._projection = RecommendationCache(catalog_ttl, 1)
        self._catalog = RecommendationCache(catalog_ttl, 8)
        self._stock = RecommendationCache(stock_ttl, 16)
        self._additional_recipes = RecommendationCache(catalog_ttl, min(capacity, 64))
        self.ingredient_profiles = RecommendationCache(catalog_ttl, capacity)
        self.http = httpx.Client(trust_env=False, follow_redirects=False,
                                 timeout=httpx.Timeout(10.0, connect=3.0),
                                 limits=httpx.Limits(max_connections=32, max_keepalive_connections=16,
                                                     keepalive_expiry=30))
        self.io = ThreadPoolExecutor(max_workers=6, thread_name_prefix="level2-io")
        self._bom_io = ThreadPoolExecutor(max_workers=3, thread_name_prefix="level2-bom")
        self.customer_taste = CustomerTasteClient(settings, client=self.http)

    def close(self):
        self.io.shutdown()
        self._bom_io.shutdown()
        self.http.close()

    def _orders(self, db, customer_id, cutoff):
        return self._evidence.get(customer_id, lambda: load_order_evidence(db, customer_id, cutoff))

    def product_history(self, repository, db, customer_id, cutoff):
        evidence = self._orders(db, customer_id, cutoff)
        return self._products.get((customer_id, evidence), lambda: repository.history(
            db, customer_id, cutoff, evidence=evidence,
        ))

    def ingredient_history(self, db, customer_id, cutoff):
        evidence = self._orders(db, customer_id, cutoff)
        return self._ingredients.get((customer_id, evidence), lambda: tuple(retained_from_evidence(evidence)))

    def survey(self, customer_id):
        return self._surveys.get(customer_id, lambda: self.customer_taste.latest(customer_id))

    def projection(self):
        source = get_taste_catalog_sync()
        view = self._projection.get("taste", source.read)
        if (view.source_read_at and view.synced_at
                and datetime.now(timezone.utc) - min(view.source_read_at, view.synced_at) >= source.ttl):
            return source.read()
        return view

    def catalog(self, timeout: float) -> CatalogClient:
        return CatalogClient(self.settings.catalog_service_url, timeout=timeout, client=self.http)

    def product_market(self):
        def load():
            client = self.catalog(self.settings.level2_stock_timeout_seconds)
            products = self.io.submit(copy_context().run, client.get_products)
            variants = client.get_product_variants(sellable_only=True)
            if len({row.id for row in variants}) != len(variants):
                raise ValueError("Duplicate Catalog variant")
            return tuple(products.result()), tuple(variants)

        return self._catalog.get("product_market", load)

    def product_mirror(self, db, repository):
        return self._catalog.get("product_mirror", lambda: repository.active_catalog(db))

    def available_variant_ids(self, variant_ids, settings):
        key = ("product_stock", frozenset(variant_ids))
        return self._stock.get(
            key,
            lambda: frozenset(fetch_available_variant_ids(
                variant_ids, settings, client=self.http, executor=self.io,
            )),
        )

    def popularity_totals(self, db) -> dict[str, float]:
        def load():
            if db is None:
                return {}
            rows = db.execute(
                select(
                    CustomerInteraction.product_id,
                    func.coalesce(func.sum(CustomerInteraction.total_purchase_count), 0),
                ).group_by(CustomerInteraction.product_id)
            )
            return {product_id: float(total) for product_id, total in rows}

        return self._catalog.get("popularity_totals", load)

    def retail_ingredients(self):
        def load():
            catalog = self.catalog(self.settings.taste_catalog_timeout_seconds)
            units = self._catalog.get("retail_units", lambda: load_catalog_units(catalog))
            return load_retail_catalog(catalog, units=units)

        return self._catalog.get("retail_ingredients", load)

    def warehouse_units(self):
        return self._catalog.get(
            "warehouse_units",
            lambda: warehouse_ingredient_units(self.settings, self.http),
        )

    def warehouse_stock(self, candidates, branch_id, *, units):
        return self._stock.get(
            ("ingredient_stock", branch_id),
            lambda: warehouse_ingredient_stock(
                self.settings, candidates, branch_id, client=self.http, units=units,
            ),
        )

    def ingredient_market(self, build_graph):
        def load():
            client = self.catalog(self.settings.taste_catalog_timeout_seconds)
            rows = client.fetch_all_pages("/api/catalog/product-variants", {"status": "ACTIVE"})
            active_ids = {UUID(row["id"]) for row in rows if row["status"] == "ACTIVE"
                          and row["productStatus"] == "ACTIVE" and row["sku"] != "CUSTOM-BUNDLE"
                          and str(row.get("productType") or "").upper() != "CUSTOMER"}
            recipes = self._recipes(active_ids)
            return active_ids, recipes, build_graph(recipes[i] for i in sorted(recipes))

        return self._catalog.get("ingredient_market", load)

    def additional_recipes(self, ids):
        if not ids:
            return {}
        return self._additional_recipes.get(frozenset(ids), lambda: self._recipes(ids))

    def _recipes(self, ids):
        return fetch_bom_recipes(self.settings, ids, timeout=self.settings.taste_catalog_timeout_seconds,
                                 client=self.http, executor=self._bom_io)

    def warm_up(self, build_ingredient_graph=None, artifact_store=None) -> None:
        """Best-effort background warmup of shared catalog, projection, and unit metadata."""
        try:
            futures = [
                self.io.submit(self.projection),
                self.io.submit(self.product_market),
                self.io.submit(self.retail_ingredients),
                self.io.submit(self.warehouse_units),
            ]
            if build_ingredient_graph is not None:
                futures.append(self.io.submit(self.ingredient_market, build_ingredient_graph))
            if artifact_store is not None:
                try:
                    artifact_store.load()
                except Exception as exc:
                    logger.debug("Artifact warmup skipped: %s", exc)
            with SessionLocal() as db:
                try:
                    self.product_mirror(db, V1RecommendationRepository())
                    self.popularity_totals(db)
                except Exception as exc:
                    logger.debug("DB catalog warmup skipped: %s", exc)
            for future in futures:
                try:
                    future.result()
                except Exception as exc:
                    logger.debug("HTTP warmup skipped: %s", exc)
        except Exception as exc:
            logger.debug("Level2 serving warmup skipped: %s", exc)


@lru_cache
def get_level2_inputs() -> Level2ServingInputs:
    return Level2ServingInputs(get_settings())
