from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from decimal import Decimal
from math import fsum, isfinite, log1p, sqrt
from pathlib import Path
from statistics import mean
from uuid import UUID

import httpx
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.clients.bom_catalog_client import BomRecipe, CatalogBomUnavailable, fetch_bom_recipes
from app.core.internal_auth import generate_internal_auth_headers
from app.core.settings import Settings
from app.recommendation.taste_ranking_features import MIN_VALID_ORDERS
from app.repositories.explore_history_repository import ExploreHistoryRepository
from app.repositories.ingredient_history_repository import RetainedLine, retained_lines
from app.schemas.bom_recommendation import (
    BomAvailability, BomHistoryLine, BomIngredientStock, BomQuantityBasis, BomRecommendationItem,
    BomRecommendations, BundlePrefill,
)
from app.schemas.taste_catalog import Digest
from app.service.level2_availability import AvailabilityUnavailable, _validate_inventory_origin
from app.service.personalized_taste_features import PersonalizedTasteFeatures, TasteFeatureBatch


class CandidateApproval(BaseModel):
    variantId: UUID
    compositionRevision: int = Field(gt=0)
    compositionSha256: Digest
    provenance: str = Field(min_length=1, max_length=500)
    preparationCode: str | None = Field(default=None, max_length=80)
    preparationSha256: Digest | None = None


class CandidateManifest(BaseModel):
    candidates: list[CandidateApproval] = Field(max_length=64)


class StockResponse(BaseModel):
    enough: bool
    lines: list[BomIngredientStock]


class InventoryIngredient(BaseModel):
    referenceId: UUID
    unitId: UUID | None
    itemType: str
    status: str


class InventoryIngredientPage(BaseModel):
    content: list[InventoryIngredient]
    last: bool


def unit_vector(vector: dict[UUID, float]) -> dict[UUID, float]:
    norm = sqrt(fsum(value * value for value in vector.values()))
    if not isfinite(norm) or norm == 0:
        return {}
    return {ingredient: value / norm for ingredient, value in sorted(vector.items()) if value > 0}


def ingredient_profile(lines: list[RetainedLine], recipes: dict[UUID, BomRecipe]) -> tuple[dict[UUID, float], list[BomHistoryLine]]:
    weights: dict[UUID, float] = defaultdict(float)
    evidence = []
    for line in lines:
        recipe = recipes.get(line.variant_id) if line.quantity else None
        if recipe and recipe.row.productId != line.product_id:
            recipe = None
        quality = "FULLY_RETURNED" if not line.quantity else "CURRENT_BOM_PROXY" if recipe else "UNRESOLVED"
        evidence.append(BomHistoryLine(
            orderItemId=line.order_item_id, variantId=line.variant_id, retainedQuantity=line.quantity,
            sourceQuality=quality, compositionRevision=recipe.row.compositionRevision if recipe else None,
            compositionSha256=recipe.row.compositionSha256 if recipe else None,
        ))
        if recipe:
            weight = log1p(float(min(line.quantity, Decimal(3))))
            for ingredient, share in recipe.vector.items():
                weights[ingredient] += weight * share
    return unit_vector(weights), evidence


def taste_compatibility(batch: TasteFeatureBatch, recipe: BomRecipe,
                        approval: CandidateApproval) -> tuple[str, float | None]:
    if not approval.preparationCode or not approval.preparationSha256:
        return "PREPARATION_UNVERIFIED", None
    product = (batch.products or {}).get(str(recipe.row.productId))
    if not product:
        return batch.status if batch.status != "TASTE_FEATURE_READY" else "PROFILE_MISSING", None
    variants = [v for v in product.variants
                if v.variant_id == str(recipe.row.variantId) and v.composition_sha256 == recipe.row.compositionSha256
                and v.composition_revision == recipe.row.compositionRevision
                and v.preparation_code == approval.preparationCode and v.preparation_sha256 == approval.preparationSha256]
    if len(variants) != 1:
        return "PROFILE_UNAVAILABLE_FOR_COMPOSITION", None
    features = variants[0]
    if False in features.hard_matches.values():
        return "AVAILABLE", 0.0
    if None in features.hard_matches.values():
        return "SENSORY_UNKNOWN", None
    soft = [v for v in features.soft_compatibility.values() if v is not None]
    if soft:
        return "AVAILABLE", mean(soft)
    if features.hard_matches and not features.soft_compatibility:
        return "AVAILABLE", 1.0
    return "SENSORY_UNKNOWN", None


def load_candidate_manifest(path: str | None) -> dict[UUID, CandidateApproval]:
    if not path:
        return {}
    with Path(path).open("rb") as stream:
        content = stream.read(65537)
    if len(content) > 65536:
        raise ValueError("BOM candidate manifest exceeds size limit")
    rows = CandidateManifest.model_validate_json(content).candidates
    approvals = {row.variantId: row for row in rows}
    if len(approvals) != len(rows):
        raise ValueError("Duplicate approved candidate")
    if any(bool(row.preparationCode) != bool(row.preparationSha256) for row in rows):
        raise ValueError("Incomplete preparation approval")
    return approvals


def warehouse_checks(settings: Settings, recipes: list[BomRecipe], branch_id: UUID | None) -> dict[UUID, BomAvailability]:
    origin = _validate_inventory_origin(settings.level2_inventory_service_url)
    with httpx.Client(base_url=origin, timeout=settings.level2_stock_timeout_seconds, trust_env=False) as client:
        units: dict[UUID, list[UUID | None]] = defaultdict(list)
        for page in range(20):
            response = client.get("/api/inventory/inventory-items", params={
                "itemType": "INGREDIENT", "status": "ACTIVE", "size": 100, "page": page,
            }, headers=generate_internal_auth_headers())
            response.raise_for_status()
            result = InventoryIngredientPage.model_validate_json(response.content)
            for item in result.content:
                if item.itemType == "INGREDIENT" and item.status == "ACTIVE":
                    units[item.referenceId].append(item.unitId)
            if result.last:
                break
        else:
            raise AvailabilityUnavailable("Incomplete unit mapping")

        def check(recipe: BomRecipe) -> tuple[UUID, BomAvailability] | None:
            if not recipe.lines or any(units[line.ingredientId] != [line.unitId] for line in recipe.lines):
                return None
            response = client.post("/internal/inventory/custom-bundles/availability", json={
                "branchId": str(branch_id) if branch_id else None,
                "lines": [{"ingredientId": str(line.ingredientId), "quantity": str(line.quantity)}
                          for line in recipe.lines],
            }, headers=generate_internal_auth_headers())
            response.raise_for_status()
            result = StockResponse.model_validate_json(response.content)
            expected = {line.ingredientId: line.quantity for line in recipe.lines}
            if len(result.lines) != len(expected) or {x.ingredientId: x.requiredQuantity for x in result.lines} != expected:
                raise AvailabilityUnavailable("Inventory line mismatch")
            if not result.enough or any(x.missingQuantity or x.availableQuantity < x.requiredQuantity for x in result.lines):
                return None
            return recipe.row.variantId, BomAvailability(branchId=branch_id, lines=result.lines)

        with ThreadPoolExecutor(max_workers=4) as executor:
            return dict(result for result in executor.map(check, recipes) if result is not None)


class BomRecommendationService:
    def __init__(self, settings: Settings, taste: PersonalizedTasteFeatures):
        self.settings = settings
        self.taste = taste

    def recommend(self, db: Session, customer_id: str, limit: int, branch_id: UUID | None,
                  selected: tuple[UUID, int, str] | None) -> BomRecommendations:
        now = datetime.now(timezone.utc)
        cutoff = now.replace(tzinfo=None)
        count = ExploreHistoryRepository().history(db, customer_id, cutoff).valid_order_count
        base = BomRecommendations(customerId=UUID(customer_id), validOrderCount=count, status="INSUFFICIENT_VALID_ORDERS")
        if count < MIN_VALID_ORDERS:
            return base
        lines = retained_lines(db, customer_id, cutoff)
        manifest_error = False
        try:
            approvals = load_candidate_manifest(self.settings.bom_candidate_manifest_path)
        except (OSError, ValueError):
            approvals = {}
            manifest_error = True
        if selected:
            requested_id, revision, checksum = selected
            approved = approvals.get(requested_id)
            if not approved or (approved.compositionRevision, approved.compositionSha256) != (revision, checksum):
                return base.model_copy(update={"status": "SELECTION_STALE"})
            approvals = {requested_id: approved}
        try:
            ids = {line.variant_id for line in lines if line.quantity} | approvals.keys()
            if len(ids) > 512:
                raise CatalogBomUnavailable("Recipe scope exceeds limit")
            recipes = fetch_bom_recipes(self.settings, ids)
        except CatalogBomUnavailable:
            return base.model_copy(update={"status": "CATALOG_UNAVAILABLE"})

        profile, evidence = ingredient_profile(lines, recipes)
        base.history = evidence
        base.ingredientVector = profile
        if not profile:
            return base.model_copy(update={"status": "INSUFFICIENT_INGREDIENT_SIGNAL"})
        base.sourceQuality = "CURRENT_BOM_PROXY"
        if not approvals or manifest_error:
            return base.model_copy(update={"status": "CANDIDATE_POOL_UNAVAILABLE"})

        candidates = []
        for variant_id, rule in approvals.items():
            recipe = recipes.get(variant_id)
            if (recipe and recipe.lines and
                    (recipe.row.compositionRevision, recipe.row.compositionSha256)
                    == (rule.compositionRevision, rule.compositionSha256)):
                candidates.append(recipe)
        if not candidates:
            return base.model_copy(update={"status": "SELECTION_STALE" if selected else "NO_AVAILABLE_CANDIDATES"})
        try:
            available = warehouse_checks(self.settings, candidates, branch_id)
        except (httpx.HTTPError, ValueError, AvailabilityUnavailable):
            return base.model_copy(update={"status": "AVAILABILITY_UNAVAILABLE"})

        stocked = [recipe for recipe in candidates if recipe.row.variantId in available]
        if not stocked:
            return base.model_copy(update={"status": "NO_AVAILABLE_CANDIDATES"})
        taste = self.taste.prepare(customer_id, datetime.now(timezone.utc), {str(recipe.row.productId) for recipe in stocked})
        scored = []
        for recipe in stocked:
            shares = recipe.vector
            contributions = {ingredient: weight * profile.get(ingredient, 0) for ingredient, weight in shares.items()}
            similarity = min(1.0, fsum(contributions.values()))
            if not isfinite(similarity) or similarity <= 0:
                continue
            taste_status, taste_score = taste_compatibility(taste, recipe, approvals[recipe.row.variantId])
            final = similarity if taste_score is None else 0.8 * similarity + 0.2 * taste_score
            match = [recipe.names[i] for i in sorted(contributions, key=lambda i: (-contributions[i], i))
                     if contributions[i] > 0][:3]
            scored.append((final, similarity, recipe, taste_status, taste_score, match))

        seen = set()
        items = []
        for final, similarity, recipe, taste_status, taste_score, match in sorted(
                scored, key=lambda hit: (-hit[0], -hit[1], hit[2].row.variantId)):
            if recipe.ratio in seen:
                continue
            seen.add(recipe.ratio)
            items.append(BomRecommendationItem(
                rank=len(items) + 1, variantId=recipe.row.variantId, sku=recipe.row.sku,
                compositionRevision=recipe.row.compositionRevision, compositionSha256=recipe.row.compositionSha256,
                ingredientSimilarity=similarity, tasteStatus=taste_status, tasteScore=taste_score,
                finalScore=final, matchingIngredients=match,
                reason="Có nguyên liệu trùng với công thức hiện hành của sản phẩm khách còn giữ: " + ", ".join(match),
                quantityBasis=BomQuantityBasis(ingredientInputGrams=sum(recipe.grams.values())),
                availability=available[recipe.row.variantId],
                bundlePrefill=BundlePrefill(bundleName=recipe.row.name, lines=list(recipe.lines or ())),
            ))
            if len(items) == limit:
                break
        return base.model_copy(update={"status": "OK" if items else "INSUFFICIENT_INGREDIENT_SIGNAL", "items": items})
