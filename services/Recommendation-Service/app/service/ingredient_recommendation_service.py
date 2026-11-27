from collections import Counter, defaultdict
from contextvars import copy_context
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from itertools import combinations
from math import exp2, log1p, sqrt
from uuid import UUID

import httpx
from sqlalchemy.orm import Session

from app.clients.bom_catalog_client import CatalogBomUnavailable, fetch_bom_recipes
from app.clients.catalog_client import CatalogClient
from app.clients.ingredient_availability_client import (
    load_retail_catalog, warehouse_ingredient_stock,
)
from app.core.settings import Settings
from app.recommendation.taste_ranking_features import MIN_VALID_ORDERS
from app.repositories.explore_history_repository import ExploreHistoryError
from app.repositories.ingredient_history_repository import RetainedLine, retained_lines
from app.schemas.ingredient_recommendation import IngredientRecommendationItem, IngredientRecommendations
from app.service.level2_availability import AvailabilityUnavailable
from app.service.level2_serving_inputs import Level2ServingInputs

PROXY = "PRODUCT_BOM_PROXY"
DIRECT = "DIRECT_CUSTOM_BUNDLE_COMPONENT"
PAIR = "CATALOG_BOM_CO_OCCURRENCE"
PRIOR = "CATALOG_BOM_PRIOR"
GRAMS_PER_UNIT = {"kg": Decimal(1000), "g": Decimal(1), "mg": Decimal("0.001"),
                  "µg": Decimal("0.000001"), "μg": Decimal("0.000001")}


def direct_grams(line: RetainedLine) -> dict[UUID, Decimal]:
    grams: dict[UUID, Decimal] = defaultdict(Decimal)
    for component in line.components:
        try:
            scale = GRAMS_PER_UNIT.get(str(component["unit"]).lower())
            if scale is None:
                return {}  # A partial denominator would inflate every recognized ingredient.
            grams[UUID(str(component["ingredient_id"]))] += Decimal(str(component["quantity"])) * scale
        except (KeyError, TypeError, ValueError, InvalidOperation) as exc:
            raise ExploreHistoryError("Invalid custom bundle component evidence") from exc
    return dict(grams)


def recipe_for_line(line, recipes):
    recipe = recipes.get(line.variant_id)
    return recipe if recipe and recipe.row.productId == line.product_id else None


def behavioral_profile(lines, recipes, cutoff, settings):
    weights: dict[UUID, float] = defaultdict(float)
    evidence = defaultdict(set)
    for line in lines:
        if line.quantity <= 0:
            continue
        if line.purchase_type == "CUSTOM_BUNDLE":
            source, source_weight, grams = DIRECT, settings.ingredient_direct_weight, direct_grams(line)
        else:
            recipe = recipe_for_line(line, recipes)
            if recipe is None:
                continue
            source, source_weight, grams = PROXY, settings.ingredient_proxy_weight, recipe.grams
        if not grams:
            continue
        age = max(0.0, (cutoff - line.occurred_at).total_seconds() / 86400)
        weight = (source_weight * log1p(float(min(line.quantity, settings.ingredient_retained_quantity_cap)))
                  * exp2(-age / settings.ingredient_recency_half_life_days))
        total = sum(grams.values())
        for ingredient, amount in grams.items():
            contribution = weight * float(amount / total)
            if contribution > 0:
                weights[ingredient] += contribution
                evidence[ingredient].add(source)
    maximum = max(weights.values(), default=1.0)
    return {ingredient: score / maximum for ingredient, score in weights.items()}, evidence


def build_catalog_signals(recipes, min_pair_boms):
    frequencies = Counter()
    pairs = Counter()
    for recipe in recipes:
        members = sorted(recipe.grams)
        frequencies.update(members)
        pairs.update(combinations(members, 2))
    peak = max(frequencies.values(), default=1)
    prior = {ingredient: count / peak for ingredient, count in frequencies.items()}
    neighbors = defaultdict(dict)
    for (ingredient, chosen), count in pairs.items():
        if count >= min_pair_boms:
            score = count / sqrt(frequencies[ingredient] * frequencies[chosen])
            neighbors[chosen][ingredient] = neighbors[ingredient][chosen] = score
    return prior, dict(neighbors)


def selected_pair_scores(neighbors, selected):
    scores = defaultdict(float)
    for chosen in selected:
        for ingredient, score in neighbors.get(chosen, {}).items():
            if ingredient not in selected:
                scores[ingredient] = max(scores[ingredient], score)
    return scores


def catalog_signals(recipes, selected, min_pair_boms):
    prior, neighbors = build_catalog_signals(recipes, min_pair_boms)
    return prior, selected_pair_scores(neighbors, selected)


class IngredientRecommendationService:
    def __init__(self, settings: Settings, serving_inputs: Level2ServingInputs | None = None):
        self.settings = settings
        self.serving_inputs = serving_inputs

    def recommend(self, db: Session, customer_id: str, limit: int, branch_id: UUID | None,
                  selected: set[UUID]) -> IngredientRecommendations:
        cutoff = datetime.now(timezone.utc).replace(tzinfo=None)
        lines = (self.serving_inputs.ingredient_history(db, customer_id, cutoff) if self.serving_inputs else
                 retained_lines(db, customer_id, cutoff, include_bundles=True))
        count = len({line.order_id for line in lines if line.quantity > 0
                     and (line.purchase_type == "PRODUCT" or line.components)})
        result = IngredientRecommendations(customerId=UUID(customer_id), validOrderCount=count,
                                          status="INSUFFICIENT_VALID_ORDERS")
        if count < MIN_VALID_ORDERS:
            return result
        if self.serving_inputs:
            return self._with_shared_inputs(result, lines, cutoff, limit, branch_id, selected)
        return self._with_live_inputs(result, lines, cutoff, limit, branch_id, selected)

    def _with_shared_inputs(self, result, lines, cutoff, limit, branch_id, selected):
        cfg, inputs = self.settings, self.serving_inputs
        pending_units = inputs.io.submit(copy_context().run, inputs.warehouse_units)

        def market():
            return inputs.ingredient_market(lambda recipes: build_catalog_signals(recipes, cfg.ingredient_min_pair_boms))

        pending = inputs.io.submit(copy_context().run, market)
        try:
            retail_all = inputs.retail_ingredients()
        except (httpx.HTTPError, KeyError, TypeError, ValueError, RuntimeError):
            pending_units.cancel()
            pending.cancel()
            return result.model_copy(update={"status": "UPSTREAM_UNAVAILABLE", "unavailableSource": "CATALOG"})
        retail = {key: row for key, row in retail_all.items() if key not in selected}
        if not retail:
            pending_units.cancel()
            pending.cancel()
            return result.model_copy(update={"status": "NO_AVAILABLE_INGREDIENTS"})

        stock = None
        try:
            units = pending_units.result()
            all_stock = inputs.warehouse_stock(retail_all, branch_id, units=units)
            stock = {key: qty for key, qty in all_stock.items() if key not in selected}
        except (AvailabilityUnavailable, httpx.HTTPError, ValueError):
            pass
        try:
            active, recipes, (prior, neighbors) = pending.result()
            history_ids = {line.variant_id for line in lines if line.purchase_type == "PRODUCT" and line.quantity > 0}
            if missing := history_ids - active:
                recipes = recipes | inputs.additional_recipes(missing)
        except (CatalogBomUnavailable, httpx.HTTPError, KeyError, TypeError, ValueError, RuntimeError):
            return result.model_copy(update={"status": "UPSTREAM_UNAVAILABLE", "unavailableSource": "CATALOG"})
        if stock is None:
            return result.model_copy(update={"status": "UPSTREAM_UNAVAILABLE", "unavailableSource": "INVENTORY"})
        if not stock:
            return result.model_copy(update={"status": "NO_AVAILABLE_INGREDIENTS"})
        # All retained timestamps are <= cutoff. The common decay factor cancels in normalization.
        # Cache only when the customer, retained lines, and relevant composition digests are equal.
        line_key = tuple((line.order_item_id, line.product_id, line.variant_id, line.quantity, line.occurred_at,
                          line.purchase_type, tuple(tuple(sorted(item.items())) for item in line.components))
                         for line in lines)
        recipe_key = tuple((key, recipes[key].row.productId, recipes[key].row.compositionSha256)
                           for key in sorted(history_ids & recipes.keys()))
        behavior, sources = inputs.ingredient_profiles.get(
            (result.customerId, line_key, recipe_key), lambda: behavioral_profile(lines, recipes, cutoff, cfg),
        )
        return self._rank(result, limit, selected, retail, stock, behavior, sources,
                          prior, selected_pair_scores(neighbors, selected))

    def _with_live_inputs(self, result, lines, cutoff, limit, branch_id, selected):
        cfg = self.settings
        try:
            with CatalogClient(cfg.catalog_service_url, timeout=cfg.taste_catalog_timeout_seconds) as catalog:
                retail = {key: row for key, row in load_retail_catalog(catalog).items() if key not in selected}
                if not retail:
                    return result.model_copy(update={"status": "NO_AVAILABLE_INGREDIENTS"})
                variants = catalog.fetch_all_pages("/api/catalog/product-variants", {"status": "ACTIVE"})
            active_ids = {UUID(row["id"]) for row in variants if row["status"] == "ACTIVE"
                          and row["productStatus"] == "ACTIVE" and row["sku"] != "CUSTOM-BUNDLE"
                          and str(row.get("productType") or "").upper() != "CUSTOMER"}
            history_ids = {line.variant_id for line in lines if line.purchase_type == "PRODUCT" and line.quantity > 0}
            recipes = fetch_bom_recipes(cfg, active_ids | history_ids, timeout=cfg.taste_catalog_timeout_seconds)
        except (CatalogBomUnavailable, httpx.HTTPError, KeyError, TypeError, ValueError):
            return result.model_copy(update={"status": "UPSTREAM_UNAVAILABLE", "unavailableSource": "CATALOG"})
        try:
            stock = warehouse_ingredient_stock(cfg, retail, branch_id)
        except (AvailabilityUnavailable, httpx.HTTPError, ValueError):
            return result.model_copy(update={"status": "UPSTREAM_UNAVAILABLE", "unavailableSource": "INVENTORY"})
        if not stock:
            return result.model_copy(update={"status": "NO_AVAILABLE_INGREDIENTS"})

        behavior, sources = behavioral_profile(lines, recipes, cutoff, cfg)
        prior, pair = catalog_signals((recipes[i] for i in sorted(active_ids & recipes.keys())),
                                     selected, cfg.ingredient_min_pair_boms)
        return self._rank(result, limit, selected, retail, stock, behavior, sources, prior, pair)

    def _rank(self, result, limit, selected, retail, stock, behavior, sources, prior, pair):
        cfg = self.settings
        if selected:
            bw, pw, cw = cfg.ingredient_selected_weights
        else:
            bw, cw = cfg.ingredient_initial_weights
            pw = 0.0
        items = []
        for ingredient, quantity in stock.items():
            b, p, c = behavior.get(ingredient, 0.0), pair.get(ingredient, 0.0), prior.get(ingredient, 0.0)
            score = min(1.0, bw * b + pw * p + cw * c)
            if score <= 0:
                continue
            evidence = sources.get(ingredient, set())
            reason = ("Khách đã chọn nguyên liệu này trong đơn đóng trà" if DIRECT in evidence else
                      "Có trong BOM hiện hành của sản phẩm khách đã mua" if PROXY in evidence else
                      "Thường xuất hiện cùng nguyên liệu đã chọn trong BOM" if p else
                      "Xuất hiện trong BOM đang hoạt động")
            item = retail[ingredient]
            items.append(IngredientRecommendationItem(
                ingredientId=ingredient, ingredientCode=item.ingredientCode, ingredientName=item.name,
                unitId=item.unitId, unitSymbol=item.unitSymbol, retailPricePerUnit=item.retailPricePerUnit,
                availableQuantity=quantity, behavioralScore=b, pairScore=p, priorScore=c, finalScore=score,
                reason=reason, evidenceSources=sorted(evidence | ({PAIR} if p else set()) | ({PRIOR} if c else set())),
                sourceQuality="CURRENT_BOM_PROXY" if PROXY in evidence else None,
            ))
        items.sort(key=lambda item: (-item.finalScore, -item.behavioralScore, -item.pairScore, item.ingredientId))
        items = items[:limit]
        status = ("NO_INGREDIENT_EVIDENCE" if not items else "OK" if any(item.behavioralScore for item in items)
                  else "FALLBACK_SIMILAR_RETAIL")
        return result.model_copy(update={"status": status, "items": items})
