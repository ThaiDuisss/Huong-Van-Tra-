import json
from decimal import Decimal
from math import isclose
from types import SimpleNamespace
from unittest import TestCase
from unittest.mock import patch
from uuid import UUID

import httpx

from app.clients.bom_catalog_client import RecipeBatch, RecipeRow, normalize_recipe
from app.schemas.taste_catalog import digest
from app.service import bom_recommendation_service as bom
from app.service.personalized_taste_features import ProductTasteFeatures, TasteFeatureBatch, VariantTasteFeatures


def uid(number):
    return UUID(int=number)


def recipe(number, grams_a, grams_b=0):
    unit = {"unitId": str(uid(1)), "deleted": False, "status": "ACTIVE", "type": "WEIGHT"}
    basis = {
        "ruleVersion": 1, "variantId": str(uid(number)),
        "ingredients": [
            {"ingredientId": str(uid(i)), "kind": 1, "status": "ACTIVE", "deleted": False,
             "unit": [unit], "ingredientUnit": [unit], "quantity": str(grams), "waste": 90}
            for i, grams in [(2, grams_a), (3, grams_b)] if grams
        ] + [{"ingredientId": str(uid(4)), "kind": 0, "quantity": "500"}],
    }
    return normalize_recipe(
        RecipeRow(variantId=uid(number), productId=uid(10), name="Test recipe", sku=str(number),
                  compositionRevision=2, compositionSha256=digest(basis), basis=basis),
        RecipeBatch(recipes=[], units={uid(1): {"symbol": "g", "precisionScale": 3}},
                    ingredients={uid(i): {"code": str(i), "name": str(i), "retailPricePerUnit": 500}
                                 for i in [2, 3]}),
    )


def approval(row, **extra):
    return bom.CandidateApproval(variantId=row.row.variantId, compositionRevision=row.row.compositionRevision,
                                 compositionSha256=row.row.compositionSha256, provenance="test-only", **extra)


def check_return_aware_ranking():
    pure, scaled, blend, unrelated, stale = [recipe(*args) for args in
        [(11, 5), (12, 50), (13, 5, 5), (14, 0, 5), (15, 5)]]
    rows = {r.row.variantId: r for r in [pure, scaled, blend, unrelated, stale]}
    approvals = {i: approval(row) for i, row in rows.items()}
    approvals[uid(15)].compositionRevision = 1
    retained = [
        bom.RetainedLine(uid(31), uid(10), uid(11), Decimal(1)),
        bom.RetainedLine(uid(32), uid(10), uid(14), Decimal(0)),
    ]

    def available(_, candidates, branch):
        assert {r.row.variantId for r in candidates} == set(rows) - {uid(15)}
        return {row.row.variantId: bom.BomAvailability(branchId=branch, lines=[]) for row in candidates}

    service = bom.BomRecommendationService(
        SimpleNamespace(bom_candidate_manifest_path=None),
        SimpleNamespace(prepare=lambda *_: TasteFeatureBatch("SURVEY_MISSING")),
    )
    with (patch.object(bom.ExploreHistoryRepository, "history", return_value=SimpleNamespace(valid_order_count=5)),
          patch.object(bom, "retained_lines", return_value=retained),
          patch.object(bom, "load_candidate_manifest", return_value=approvals),
          patch.object(bom, "fetch_bom_recipes", return_value=rows),
          patch.object(bom, "warehouse_checks", side_effect=available)):
        result = service.recommend(None, str(uid(25)), 5, None, None)
    assert result.status == "OK"
    assert [r.sourceQuality for r in result.history] == ["CURRENT_BOM_PROXY", "FULLY_RETURNED"]
    assert result.ingredientVector == {uid(2): 1.0}
    assert [r.variantId for r in result.items] == [uid(11), uid(13)]
    assert isclose(result.items[0].finalScore, 1)
    assert isclose(result.items[1].finalScore, 2 ** -0.5)
    assert result.items[0].quantityBasis.ingredientInputGrams == 5
    assert all(r.tasteScore is None for r in result.items)


def check_exact_taste_match():
    row = recipe(11, 2)
    prepared = "a" * 64
    features = VariantTasteFeatures(
        str(row.row.variantId), "approved", 1, 2, row.row.compositionSha256,
        "HOT", prepared, {}, {"bitterness": 0.75, "aroma": None},
    )
    batch = TasteFeatureBatch("TASTE_FEATURE_READY", {
        str(uid(10)): ProductTasteFeatures("AVAILABLE", (features,)),
    })
    assert bom.taste_compatibility(batch, row, approval(row, preparationCode="HOT",
        preparationSha256=prepared)) == ("AVAILABLE", 0.75)
    assert bom.taste_compatibility(batch, row, approval(row, preparationCode="COLD",
        preparationSha256=prepared)) == ("PROFILE_UNAVAILABLE_FOR_COMPOSITION", None)


def check_stock_shortage():
    branch = uid(44)
    row = recipe(11, 5)
    enough = False

    def handle(request):
        if request.method == "GET":
            return httpx.Response(200, json={"last": True, "content": [
                {"referenceId": str(uid(2)), "unitId": str(uid(1)), "itemType": "INGREDIENT", "status": "ACTIVE"},
            ]})
        assert request.url.path == "/internal/inventory/custom-bundles/availability"
        assert json.loads(request.content) == {"branchId": str(branch), "lines": [
            {"ingredientId": str(uid(2)), "quantity": "5"},
        ]}
        return httpx.Response(200, json={"enough": enough, "lines": [
            {"ingredientId": str(uid(2)), "requiredQuantity": "5",
             "availableQuantity": "5" if enough else "4", "missingQuantity": "0" if enough else "1"},
        ]})

    client = httpx.Client
    settings = SimpleNamespace(level2_inventory_service_url="http://localhost:8084", level2_stock_timeout_seconds=3)
    with (patch.object(bom.httpx, "Client", side_effect=lambda **kwargs: client(
                transport=httpx.MockTransport(handle), **kwargs)),
          patch.object(bom, "generate_internal_auth_headers", return_value={})):
        assert bom.warehouse_checks(settings, [row], branch) == {}
        enough = True
        assert bom.warehouse_checks(settings, [row], branch)[uid(11)].branchId == branch


class BomRecommendationTest(TestCase):
    def test_return_aware_cosine_and_scaled_recipe_dedupe(self):
        check_return_aware_ranking()

    def test_taste_requires_exact_approved_revision_and_preparation(self):
        check_exact_taste_match()

    def test_availability_uses_original_amount_and_rejects_shortage(self):
        check_stock_shortage()
