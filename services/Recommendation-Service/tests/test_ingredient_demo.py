"""Opt-in integration against verified external Phase 2/3 artifacts, without fitting."""
from copy import deepcopy
from datetime import datetime, timezone
from decimal import Decimal
from io import BytesIO
import json
import os
from pathlib import Path
import secrets
import shutil
from tempfile import TemporaryDirectory
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient
from openpyxl import load_workbook

from app.ingredient_demo.api import create_app
from app.ingredient_demo.bom_report import PROTOCOL, build_report_snapshot
from app.ingredient_demo.bom_display import customer_codes
from app.ingredient_demo.service import ArtifactVerificationError
from app.ingredient_demo.settings import DemoConfigurationError, IngredientDemoSettings
from app.ingredient_demo.workbook import DATE, HEADERS, HEADER_ROWS, PERCENT, SHEETS, WARNING, build_workbook, recipe_labels, table_rows
from app.ingredient_poc.contracts import content_hash
from app.ingredient_poc.recommendation.data import sha256
from app.ingredient_poc.recommendation.model import IngredientRecommender
from app.ingredient_poc.synthetic import END, T1, T2

PREFIX = "/api/v1/ingredient-demo"
CONFIG = os.environ.get("HVTRA_INGREDIENT_TEST_CONFIG")


class DemoDisabledTests(unittest.TestCase):
    def test_default_disabled_never_opens_artifacts_and_has_no_product_routes(self):
        with patch("app.ingredient_demo.api.IngredientDemoService") as loader:
            with TestClient(create_app(IngredientDemoSettings(demo_enabled=False))) as client:
                for path in ("/customers", "/reports/excel", "/recommendations?customer_id=x&as_of=2026-04-01T00:00:00Z"):
                    self.assertEqual(client.get(PREFIX + path).status_code, 404)
                self.assertEqual(client.get("/recommendations/customer").status_code, 404)
                self.assertEqual(client.get("/openapi.json").status_code, 404)
            loader.assert_not_called()

    def test_enabled_requires_isolated_environment_auth_secret_and_explicit_artifacts(self):
        for change in ({}, {"demo_environment": "production"}, {"demo_environment": "test", "demo_auth_mode": "anonymous"},
                       {"demo_environment": "test", "demo_token": "short"}):
            with self.subTest(change=change), self.assertRaises(DemoConfigurationError):
                create_app(IngredientDemoSettings(demo_enabled=True, **change))


class BomReportBoundaryTests(unittest.TestCase):
    def model(self, *, empty=False):
        # Same family/hash is deliberate: only exact recipe identity can match.
        recipes = {rid: {"recipe_family_key": "same-family", "recipe_version": n, "recipe_hash": "same-content"}
                   for n, rid in enumerate(("recipe-a", "recipe-b"), 1)}
        def sale(line, recipe, customer="c1", quantity="1", kind="CUSTOM_BOM", when=T2, order="o1"):
            return {"order_id": order, "order_item_id": line, "customer_id": customer, "recipe_id": recipe,
                    "purchase_type": kind, "purchased_at": when, "sale_unit_id": "count", "quantity": Decimal(quantity)}
        sales = [sale("a", "recipe-a", quantity="2"), sale("b", "recipe-a", quantity="3"),
                 sale("c", "recipe-b", customer="c2"), sale("bridge", "recipe-a", customer="c2", kind="PRODUCT"),
                 sale("before", "recipe-a", when=T1), sale("end", "recipe-a", when=END)]
        data = SimpleNamespace(customers={"c1", "c2"}, recipes=recipes, id="test-dataset",
                               tables={"sale_items": sales, "units": [{"id": "count", "symbol": "count"}]},
                               candidates=lambda _: [{"recommendation_type": "CUSTOM_BOM", "recipe_id": r} for r in recipes])
        def recommend(customer, **kwargs):
            self.assertEqual(kwargs, {"as_of": T2, "mode": "CUSTOM_BOM", "policy": "REPEAT", "top_k": 5})
            rows = [] if empty else [{"recipe_id": "recipe-a", "item_id": "recipe-a", "recommendation_type": "CUSTOM_BOM"}]
            return {"customer_id": customer, "mode": "CUSTOM_BOM", "as_of": T2.isoformat(), "policy": "REPEAT",
                    "requested_k": 5, "returned_k": len(rows), "recommendations": rows}
        return SimpleNamespace(data=data, recommend=recommend)

    def test_exact_identity_repeat_order_quantity_and_time_boundaries(self):
        result = build_report_snapshot(self.model())
        self.assertEqual(result["summary"]["recommendations"], 2)  # Actual returned_k, not 2*5.
        self.assertEqual(result["summary"]["matches"], 1)
        self.assertEqual(result["summary"]["match_rate"], 0.5)
        self.assertEqual(result["evaluation"][1]["matched"], [])  # Same family/hash and Product bridge are not hits.
        first = next(r for r in result["recipes"] if r["recipe_id"] == "recipe-a")
        self.assertEqual((first["purchase_orders"], first["sale_rows"], first["sold_quantity"]), (1, 2, Decimal(5)))
        self.assertEqual(first["matched_count"], 1)  # Repeat purchases do not multiply recommendation hits.
        other = next(r for r in result["recipes"] if r["recipe_id"] == "recipe-b")
        self.assertIsNone(other["match_rate"])
        self.assertEqual(result["summary"]["test_bom_sale_rows"], 3)
        workbook = load_workbook(BytesIO(build_workbook(self.model().data, {"model_version": "test"}, result)))
        self.assertIn("2 công thức cho 2 khách hàng", workbook[SHEETS[0]]["A18"].value)
        self.assertIn("1 lượt", workbook[SHEETS[0]]["A18"].value)
        self.assertIn("50,00%", workbook[SHEETS[0]]["A18"].value)

    def test_zero_denominator_and_missing_candidates_stay_unavailable(self):
        model = self.model(empty=True)
        model.data.candidates = lambda _: []
        result = build_report_snapshot(model)
        self.assertIsNone(result["summary"]["match_rate"])
        self.assertIsNone(result["summary"]["coverage"])
        self.assertTrue(all(r["match_rate"] is None for r in result["evaluation"]))
        self.assertEqual(result["summary"]["excluded_unavailable_label_rows"], 3)
        workbook = load_workbook(BytesIO(build_workbook(model.data, {"model_version": "test"}, result)))
        self.assertEqual(workbook[SHEETS[2]]["G4"].value, "Chưa được gợi ý")
        self.assertEqual(workbook[SHEETS[3]]["F5"].value, "Không có gợi ý")

    def test_duplicate_lines_and_incompatible_sale_units_fail(self):
        model = self.model()
        model.data.tables["sale_items"].append(deepcopy(model.data.tables["sale_items"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            build_report_snapshot(model)
        model = self.model()
        model.data.tables["units"].append({"id": "box", "symbol": "box"})
        model.data.tables["sale_items"][1]["sale_unit_id"] = "box"
        with self.assertRaisesRegex(ValueError, "different sale units"):
            build_report_snapshot(model)


@unittest.skipUnless(CONFIG, "Set HVTRA_INGREDIENT_TEST_CONFIG to a verified external Phase 2/3 artifact configuration")
class IngredientDemoIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = json.loads(Path(CONFIG).read_text(encoding="utf-8"))
        cls.token = secrets.token_urlsafe(48)
        cls.fit_guard = patch.object(IngredientRecommender, "fit", side_effect=AssertionError("Demo must never refit"))
        cls.fit_guard.start()
        cls.app = create_app(cls.settings())
        cls.client = TestClient(cls.app).__enter__()
        cls.client.headers.update({"Authorization": "Bearer " + cls.token})
        cls.service = cls.app.state.ingredient_service
        cls.data = cls.service.data
        cls.customers = {g: next(c for c in sorted(cls.data.customers) if cls.data.cohort(c, T2) == g)
                         for g in ("PRODUCT_ONLY", "BOM_ONLY", "MIXED")}
        cls.cold = next(c for c in sorted(cls.data.customers) if not cls.data.history(c, T1))
        cls.excel_response = cls.client.get(PREFIX + "/reports/excel")
        cls.workbook = load_workbook(BytesIO(cls.excel_response.content))

    @classmethod
    def tearDownClass(cls):
        cls.client.__exit__(None, None, None)
        cls.fit_guard.stop()

    @classmethod
    def settings(cls, **changes):
        values = {**cls.config, "demo_enabled": True, "demo_environment": "test", "demo_token": cls.token}
        values.update(changes)
        return IngredientDemoSettings(**values)

    def query(self, customer=None, **changes):
        return {"customer_id": customer or self.customers["MIXED"], "as_of": T2.isoformat(), **changes}

    def test_all_cross_domain_cohorts_and_cold_start_run_real_inference(self):
        for cohort, mode, kind in (("PRODUCT_ONLY", "CUSTOM_BOM", "CUSTOM_BOM"), ("BOM_ONLY", "PRODUCT", "PRODUCT"),
                                   ("MIXED", "UNIFIED", None), ("COLD_START", "UNIFIED", None)):
            customer = self.cold if cohort == "COLD_START" else self.customers[cohort]
            when = T1 if cohort == "COLD_START" else T2
            response = self.client.get(PREFIX + "/recommendations", params=self.query(customer, mode=mode, as_of=when.isoformat()))
            self.assertEqual(response.status_code, 200)
            value = response.json()
            self.assertEqual(value["returned_k"], 5)
            self.assertEqual(value["source_kind"], "SYNTHETIC")
            self.assertIs(value["research_only"], True)
            self.assertIs(value["production_approved"], False)
            expected = self.service._model.recommend(customer, as_of=when, mode=mode)
            self.assertEqual(value["recommendations"], expected["recommendations"])
            if kind:
                self.assertTrue(all(row["recommendation_type"] == kind for row in value["recommendations"]))
            if cohort == "COLD_START":
                self.assertEqual(value["fallback"], "POPULARITY_COLD")

    def test_same_inputs_are_deterministic_and_top_k_is_validated(self):
        params = self.query(mode="PRODUCT", policy="REPEAT", top_k=3)
        first = self.client.get(PREFIX + "/recommendations", params=params)
        self.assertEqual(first.json(), self.client.get(PREFIX + "/recommendations", params=params).json())
        self.assertEqual(first.json()["returned_k"], 3)
        for k in (0, -1, 6, "invalid"):
            self.assertEqual(self.client.get(PREFIX + "/recommendations", params=self.query(top_k=k)).status_code, 422)

    def test_invalid_customer_modes_policy_and_research_window(self):
        self.assertEqual(self.client.get(PREFIX + "/recommendations", params=self.query("outside-dataset")).status_code, 404)
        for change in ({"mode": "OTHER"}, {"policy": "OTHER"}, {"as_of": "2026-04-01T00:00:00"},
                       {"as_of": "2026-02-01T00:00:00Z"}, {"as_of": "2026-05-02T00:00:00Z"}):
            with self.subTest(change=change):
                self.assertEqual(self.client.get(PREFIX + "/recommendations", params=self.query(**change)).status_code, 422)

    def test_all_routes_require_auth_and_never_expose_paths(self):
        for path in ("/customers", "/reports/excel", "/recommendations"):
            for headers in ({}, {"Authorization": "Bearer invalid"}):
                response = TestClient(self.app).get(PREFIX + path, params=self.query(), headers=headers)
                self.assertEqual(response.status_code, 401)
                self.assertNotIn(self.token, response.text)
                self.assertNotIn(self.config["dataset_path"], response.text)
        response = self.client.get(PREFIX + "/customers", headers=[("Authorization", "Bearer " + self.token), ("Authorization", "Bearer " + self.token)])
        self.assertEqual(response.status_code, 401)

    def test_customer_catalog_covers_all_400_without_personal_data(self):
        response = self.client.get(PREFIX + "/customers")
        self.assertEqual(response.status_code, 200)
        rows = response.json()["customers"]
        self.assertEqual(len(rows), 400)
        self.assertEqual({row["customer_id"] for row in rows}, set(self.data.customers))
        self.assertEqual({row["cohort"] for row in rows}, {"PRODUCT_ONLY", "BOM_ONLY", "MIXED"})
        self.assertFalse(any("email" in row or "name" in row for row in rows))

    def test_excel_download_has_five_sheets_and_complete_customer_rows(self):
        response = self.excel_response
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers["content-type"], "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.assertIn("Ingredient_Recommendation_Research_20260401.xlsx", response.headers["content-disposition"])
        self.assertEqual(response.headers["cache-control"], "private, no-store")
        self.assertEqual(self.workbook.sheetnames, SHEETS)
        self.assertEqual([len(list(table_rows(ws))) for ws in self.workbook.worksheets[1:]], [400, 80, 400, 348])
        for index, ws in enumerate(self.workbook.worksheets[1:], 1):
            self.assertEqual([c.value for c in ws[HEADER_ROWS[index]]], HEADERS[index])
        self.assertEqual(self.client.get(PREFIX + "/reports/excel").content, response.content)

    def test_excel_totals_and_all_type_specific_denominators(self):
        ws = self.workbook[SHEETS[0]]
        values = {row[0]: row[1] for row in ws.iter_rows(min_row=4, max_row=12, values_only=True)}
        self.assertEqual(values["Số khách hàng được đánh giá"], 400)
        self.assertEqual(values["Tổng số công thức có trong bộ dữ liệu"], 80)
        self.assertEqual(ws["A14"].value, WARNING)
        comparison = list(table_rows(self.workbook[SHEETS[3]]))
        total, hits = sum(r[2] for r in comparison), sum(r[4] for r in comparison)
        self.assertEqual(total, 2000)
        self.assertEqual(values["Tổng số công thức AI đã gợi ý"], total)
        self.assertEqual(values["Số lượt gợi ý trùng với công thức khách mua"], hits)
        self.assertAlmostEqual(values["Tỷ lệ gợi ý trùng với lựa chọn mua (%)"], hits / total)
        recipes = list(table_rows(self.workbook[SHEETS[2]]))
        self.assertEqual(sum(row[3] for row in recipes), total)
        self.assertEqual(sum(row[5] for row in recipes), hits)
        purchases = [s for s in self.data.tables["sale_items"] if s["purchase_type"] == "CUSTOM_BOM" and T2 <= s["purchased_at"] < END]
        self.assertEqual(sum(Decimal(str(row[7])) for row in recipes), sum(s["quantity"] for s in purchases))
        self.assertEqual(len(list(table_rows(ws))), 9)  # KPI only, no duplicate 80-recipe table.
        for row in recipes:
            if row[3]:
                self.assertAlmostEqual(row[6], row[5] / row[3])
            else:
                self.assertEqual(row[6], "Chưa được gợi ý")

    def test_excel_numeric_percentages_dates_visibility_and_no_formulas(self):
        self.assertIs(self.workbook.views[0].showSheetTabs, True)
        for index, ws in enumerate(self.workbook):
            self.assertIsNone(ws.freeze_panes)
            self.assertIs(ws.sheet_view.showGridLines, True)
            self.assertTrue(ws.auto_filter.ref.startswith(f"A{HEADER_ROWS[index]}:"))
            for row in ws:
                for cell in row:
                    self.assertNotEqual(cell.data_type, "f")
                    self.assertNotEqual(cell.data_type, "e")
                    if cell.number_format == PERCENT:
                        self.assertIsInstance(cell.value, (int, float))
                        self.assertTrue(0 <= cell.value <= 1)
                    if isinstance(cell.value, datetime):
                        self.assertEqual(cell.number_format.lower(), DATE.lower())

    def test_bom_protocol_is_separate_from_verified_phase3_metrics(self):
        path = Path(self.config["model_path"]).parent / "test-metrics.json"
        checks = json.loads((path.parent / "artifact-checksums.json").read_text(encoding="utf-8"))
        self.assertEqual(sha256(path), checks[path.name])
        snapshot = self.service.report_snapshot()
        self.assertEqual(snapshot["protocol"]["name"], PROTOCOL)
        self.assertEqual(snapshot["protocol"]["mode"], "CUSTOM_BOM")
        self.assertEqual(snapshot["protocol"]["policy"], "REPEAT")
        self.assertIs(snapshot["protocol"]["phase3_original_metrics"], False)
        self.assertNotIn("metrics", snapshot)
        self.assertEqual(len(snapshot["predictions"]), 400)
        self.assertEqual(len(snapshot["evaluation"]), 400)
        self.assertEqual(snapshot["summary"]["eligible_recipes"], 80)
        snapshot["summary"]["customers"] = -1
        self.assertEqual(self.service.report_snapshot()["summary"]["customers"], 400)

    def test_exact_bom_hits_quantities_orders_and_all_customer_rates_match_workbook(self):
        candidates = {row["recipe_id"] for row in self.data.candidates(T2) if row["recommendation_type"] == "CUSTOM_BOM"}
        snapshot = self.service.report_snapshot()
        records = {row["customer_id"]: row for row in snapshot["evaluation"]}
        comparisons = {row[1]: row for row in table_rows(self.workbook[SHEETS[3]])}
        details = {row[7]: row for row in table_rows(self.workbook[SHEETS[1]])}
        customers = customer_codes(self.data)
        labels = recipe_labels(self.data)
        for prediction in snapshot["predictions"]:
            c = prediction["customer_id"]
            relevant = {r["recipe_id"] for r in self.data.histories[c]
                        if r["purchase_type"] == "CUSTOM_BOM" and T2 <= r["purchased_at"] < END} & candidates
            expected = self.service._model.recommend(c, as_of=T2, mode="CUSTOM_BOM", policy="REPEAT", top_k=5)
            self.assertEqual(prediction, expected)
            ranks = [r["recipe_id"] for r in prediction["recommendations"]]
            hits = [rid for rid in ranks if rid in relevant]
            self.assertEqual(records[c]["matched"], hits)
            self.assertEqual(set(records[c]["purchased"]), relevant)
            self.assertEqual(comparisons[customers[c]][2:5], (len(ranks), len(relevant), len(hits)))
            self.assertAlmostEqual(comparisons[customers[c]][5], len(hits) / len(ranks))
            self.assertAlmostEqual(details[c][5], comparisons[customers[c]][5])
            self.assertEqual(details[c][2].splitlines(), [labels[rid] for rid in ranks])
            self.assertEqual(details[c][4], "\n".join(labels[rid] for rid in hits) or "Không có")
        recipes = {r["recipe_id"]: r for r in snapshot["recipes"]}
        for rid, row in recipes.items():
            purchases = [r for r in self.data.tables["sale_items"]
                         if r["purchase_type"] == "CUSTOM_BOM" and r["recipe_id"] == rid and T2 <= r["purchased_at"] < END]
            self.assertEqual(row["purchase_orders"], len({r["order_id"] for r in purchases}))
            self.assertEqual(row["sale_rows"], len(purchases))
            self.assertEqual(row["sold_quantity"], sum((r["quantity"] for r in purchases), Decimal(0)))

    def test_future_sales_and_feedback_are_not_profile_features(self):
        customer = self.customers["MIXED"]
        before = self.client.get(PREFIX + "/recommendations", params=self.query(customer)).json()
        clone = deepcopy(self.service._model)
        clone.data.histories = {c: [s for s in rows if s["purchased_at"] < T2] for c, rows in clone.data.histories.items()}
        clone.data.feedback = {c: [f for f in rows if f["feedback_time"] < T2] for c, rows in clone.data.feedback.items()}
        with patch.object(self.service, "_model", clone):
            after = self.client.get(PREFIX + "/recommendations", params=self.query(customer)).json()
        self.assertEqual(before, after)
        self.assertEqual(self.service._model.metadata["train_cutoff"], T1.isoformat())
        self.assertEqual(build_report_snapshot(clone)["predictions"], self.service.report_snapshot()["predictions"])

    def copied_settings(self, temporary):
        path = Path(temporary)
        for name in ("dataset.json", "manifest.json", "validation.json", "artifact-checksums.json"):
            shutil.copyfile(Path(self.config["dataset_path"]).parent / name, path / name)
        shutil.copyfile(self.config["model_path"], path / "model.json")
        return self.settings(dataset_path=path / "dataset.json", model_path=path / "model.json")

    def test_corrupt_dataset_model_or_supporting_manifest_fails_startup(self):
        for name in ("dataset.json", "model.json", "manifest.json"):
            with self.subTest(name=name), TemporaryDirectory(prefix="hvtra-demo-corrupt-") as directory:
                settings = self.copied_settings(directory)
                path = Path(directory) / name
                path.write_bytes(path.read_bytes() + b"corrupted")
                with self.assertRaises(ArtifactVerificationError) as context:
                    with TestClient(create_app(settings)):
                        self.fail("Corrupt artifacts were accepted")
                self.assertNotIn(directory, str(context.exception))

    def test_changed_artifact_after_startup_returns_sanitized_503(self):
        with TemporaryDirectory(prefix="hvtra-demo-changed-") as directory:
            settings = self.copied_settings(directory)
            with TestClient(create_app(settings)) as client:
                settings.model_path.write_bytes(b"invalid")
                response = client.get(PREFIX + "/reports/excel", headers={"Authorization": "Bearer " + self.token})
                self.assertEqual(response.status_code, 503)
                self.assertNotIn(directory, response.text)
                self.assertNotIn(self.token, response.text)

    def test_mismatched_dataset_id_and_unsupported_model_source_rejected(self):
        with self.assertRaises(ArtifactVerificationError):
            with TestClient(create_app(self.settings(dataset_id="other"))):
                self.fail("Wrong dataset was accepted")
        for field, value in (("source_kind", "ACTUAL"), ("research_only", False), ("production_approved", True)):
            with self.subTest(field=field), TemporaryDirectory(prefix="hvtra-demo-source-") as directory:
                settings = self.copied_settings(directory)
                artifact = json.loads(settings.model_path.read_text(encoding="utf-8"))
                artifact["metadata"][field] = value
                artifact.pop("model_fingerprint")
                artifact["model_fingerprint"] = content_hash(artifact)
                settings.model_path.write_text(json.dumps(artifact), encoding="utf-8")
                changed = settings.model_copy(update={"model_sha256": sha256(settings.model_path)})
                with self.assertRaises(ArtifactVerificationError):
                    with TestClient(create_app(changed)):
                        self.fail("Unsupported model origin was accepted")
