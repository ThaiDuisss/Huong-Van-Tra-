"""BOM presentation preserves original identities, snapshot lines and evaluation."""
from copy import deepcopy
from decimal import Decimal
from io import BytesIO
import json
import os
import re
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from zipfile import ZipFile

from openpyxl import load_workbook

from app.ingredient_demo.bom_display import INCOMPLETE, customer_codes, recipe_codes, recipe_names, recipe_components
from app.ingredient_demo.service import IngredientDemoService
from app.ingredient_demo.workbook import HEADERS, HEADER_ROWS, SHEETS, build_workbook, table_rows, verify_workbook
from app.ingredient_poc.recommendation.model import IngredientRecommender


CONFIG = os.environ.get("HVTRA_INGREDIENT_TEST_CONFIG")


class RecipeDisplayTests(unittest.TestCase):
    def data(self):
        return SimpleNamespace(
            recipes={rid: {"recipe_family_key": "same", "recipe_hash": "same"}
                     for rid in ("recipe-b", "recipe-a")},
            ingredients={"i": {"ingredient_code": "TEA", "name_snapshot": "Tên có trong nguồn"}},
            tables={"units": [{"id": "kg", "symbol": "kg", "dimension": "MASS", "validation_status": "VALIDATED"}]},
            items={"recipe-a": [{"recipe_id": "recipe-a", "ingredient_id": "i", "ingredient_code_snapshot": "TEA",
                                 "quantity_original": "0.02", "unit_id": "kg", "unit_symbol_snapshot": "kg",
                                 "validation_status": "VALIDATED", "waste_rate": "20"}]})

    def test_sorted_full_identity_is_stable_and_never_groups_by_family_hash(self):
        data = self.data()
        expected = {"recipe-a": "CT001", "recipe-b": "CT002"}
        self.assertEqual(recipe_codes(data), expected)
        data.recipes = dict(reversed(list(data.recipes.items())))
        self.assertEqual(recipe_codes(data), expected)
        self.assertEqual(recipe_names(data)["recipe-a"], "Công thức CT001")
        data.recipes["recipe-a"]["name_snapshot"] = "Tên công thức nguồn"
        self.assertEqual(recipe_names(data)["recipe-a"], "Tên công thức nguồn")

    def test_customer_codes_are_bijective_and_independent_of_inference_order(self):
        data = SimpleNamespace(customers=["full-z", "full-a"])
        self.assertEqual(customer_codes(data), {"full-a": "KH001", "full-z": "KH002"})
        data.customers.reverse()
        self.assertEqual(customer_codes(data), {"full-a": "KH001", "full-z": "KH002"})

    def test_original_quantity_unit_source_names_and_code_id_fallback(self):
        data = self.data()
        line = recipe_components(data, "recipe-a")[0]
        self.assertEqual(line, {"ingredient_id": "i", "name": "Tên có trong nguồn", "quantity": Decimal("0.02"), "unit": "kg"})
        data.ingredients["i"]["name_snapshot"] = " "
        self.assertEqual(recipe_components(data, "recipe-a")[0]["name"], "TEA")
        data.ingredients["i"]["ingredient_code"] = None
        data.items["recipe-a"][0]["ingredient_code_snapshot"] = None
        self.assertEqual(recipe_components(data, "recipe-a")[0]["name"], "i")

    def test_invalid_line_marks_whole_recipe_incomplete_without_inventing_amounts(self):
        for change in ({"quantity_original": "NaN"}, {"quantity_original": "Infinity"}, {"quantity_original": "0"},
                       {"quantity_original": "-1"}, {"quantity_original": None}, {"unit_id": "missing"},
                       {"unit_symbol_snapshot": "ml"}, {"ingredient_id": "missing"}, {"recipe_id": "recipe-b"},
                       {"ingredient_code_snapshot": "OTHER"}, {"validation_status": "QUARANTINED"}):
            with self.subTest(change=change):
                data = self.data()
                bad = dict(data.items["recipe-a"][0], **change)
                data.items["recipe-a"].append(bad)
                self.assertEqual(recipe_components(data, "recipe-a"), [
                    {"ingredient_id": None, "name": INCOMPLETE, "quantity": None, "unit": None}])
        for unit in ({"dimension": "VOLUME"}, {"symbol": "unknown"}, {"validation_status": "STAGED"}):
            data = self.data(); data.tables["units"][0].update(unit)
            self.assertEqual(recipe_components(data, "recipe-a")[0]["name"], INCOMPLETE)
        self.assertEqual(recipe_components(self.data(), "recipe-b")[0]["name"], INCOMPLETE)


@unittest.skipUnless(CONFIG, "Configure pinned external research artifacts")
class BomWorkbookTransparencyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        config = json.loads(Path(CONFIG).read_text(encoding="utf-8"))
        for key in ("dataset_path", "model_path"):
            config[key] = Path(config[key])
        with patch.object(IngredientRecommender, "fit", side_effect=AssertionError("No refit")):
            cls.service = IngredientDemoService(SimpleNamespace(**config))
            cls.snapshot = cls.service.report_snapshot()
            before = deepcopy(cls.snapshot)
            cls.content = build_workbook(cls.service.data, cls.service._model.metadata, cls.snapshot)
            assert cls.snapshot == before
        cls.wb = load_workbook(BytesIO(cls.content))

    def test_all_348_component_lines_and_80_codes_match_original_source_exactly(self):
        data = self.service.data; codes = recipe_codes(data)
        self.assertEqual(set(codes.values()), {f"CT{n:03d}" for n in range(1, 81)})
        ws = self.wb[SHEETS[4]]
        header = HEADER_ROWS[4]
        catalog = list(table_rows(ws))
        self.assertEqual(len(catalog), len(data.tables["recipe_items"]))
        self.assertEqual(len(catalog), 348)
        units = {r["id"]: r for r in data.tables["units"]}
        for rid in sorted(data.recipes):
            actual = [r for r in catalog if r[5] == rid]
            expected = data.items[rid]
            self.assertEqual(len(actual), len(expected))
            for item in expected:
                source = data.ingredients[item["ingredient_id"]]
                name = source["name_snapshot"] or source["ingredient_code"] or item["ingredient_id"]
                row = next(r for r in actual if r[2] == name)
                self.assertEqual(row[:2], (codes[rid], recipe_names(data)[rid]))
                self.assertEqual(Decimal(str(row[3])), item["quantity_original"])
                self.assertEqual(row[4], units[item["unit_id"]]["symbol"])
            self.assertEqual({r[0] for r in actual}, {codes[rid]})
            self.assertEqual({r[6] for r in actual}, {data.recipes[rid]["recipe_version"]})
            self.assertEqual({r[7] for r in actual}, {data.recipes[rid]["recipe_hash"]})
        self.assertEqual({r[4] for r in catalog}, {r["unit_symbol_snapshot"] for r in data.tables["recipe_items"]})
        self.assertTrue(all(ws.cell(i, 4).data_type == "n" for i in range(header + 1, ws.max_row + 1)))
        self.assertEqual(ws.auto_filter.ref, "A4:I352")
        stats = self.wb[SHEETS[2]]
        self.assertEqual(stats.auto_filter.ref, "A3:H83")
        self.assertEqual(stats.max_row, 83)  # No appended ingredient catalogue.
        for row in range(4, 84):
            link = stats.cell(row, 2).hyperlink
            self.assertIsNone(link.target)
            target = self.resolve(self.wb, link.location)
            self.assertEqual(target.parent.title, SHEETS[4])
            self.assertEqual(target.value, stats.cell(row, 2).value)
            self.assertEqual(target.row, min(i for i in range(5, 353) if ws.cell(i, 1).value == target.value))

    def test_main_tables_consistently_use_codes_and_keep_all_customer_recipe_mappings(self):
        codes = recipe_codes(self.service.data)
        self.assertEqual(self.wb.sheetnames, SHEETS)
        self.assertEqual([len(list(table_rows(s))) for s in self.wb.worksheets[1:]], [400, 80, 400, 348])
        details = {r[7]: r for r in table_rows(self.wb[SHEETS[1]])}
        customers = customer_codes(self.service.data)
        comparisons = {r[0]: r for r in table_rows(self.wb[SHEETS[3]])}
        for record in self.snapshot["evaluation"]:
            self.assertEqual(details[record["customer_id"]][0], customers[record["customer_id"]])
            self.assertEqual(comparisons[record["recommendation_id"]][1], customers[record["customer_id"]])
            for col, key in ((2, "recommended"), (3, "purchased"), (4, "matched")):
                expected = "\n".join(codes[rid] for rid in record[key]) or "Không có"
                self.assertEqual(details[record["customer_id"]][col], expected)
        for index, ws in enumerate(self.wb):
            for row in table_rows(ws):
                self.assertNotIn("research-family", str(row))
            self.assertEqual([c.value for c in ws[HEADER_ROWS[index]]][:len(HEADERS[index])], HEADERS[index])
        summary = self.snapshot["summary"]
        self.assertEqual((summary["recommendations"], summary["matches"], summary["match_rate"], summary["coverage"]), (2000, 62, .031, .9875))
        self.assertEqual(len(list(table_rows(self.wb[SHEETS[0]]))), 9)
        self.assertIn("2.000 công thức cho 400 khách hàng", self.wb[SHEETS[0]]["A18"].value)
        self.assertIn("62 lượt", self.wb[SHEETS[0]]["A18"].value)
        self.assertIn("3,10%", self.wb[SHEETS[0]]["A18"].value)
        verify_workbook(self.wb, self.service.data, self.snapshot)

    def test_source_names_cannot_become_formulas_or_external_hyperlinks(self):
        data = deepcopy(self.service.data)
        ingredient = next(iter(data.ingredients.values()))
        ingredient["name_snapshot"] = '=HYPERLINK("https://untrusted.invalid","name")'
        content = build_workbook(data, self.service._model.metadata, self.snapshot)
        workbook = load_workbook(BytesIO(content))
        cells = [c for row in workbook[SHEETS[4]] for c in row if c.value == ingredient["name_snapshot"]]
        self.assertTrue(cells)
        self.assertTrue(all(c.data_type == "s" and c.hyperlink is None for c in cells))

    @staticmethod
    def resolve(workbook, location):
        # Independently evaluate the small reference-only INDEX/MATCH definition;
        # inspect the real location, exact lookup key, worksheet and cell value.
        if location in workbook.defined_names:
            formula = workbook.defined_names[location].attr_text
            match = re.fullmatch(r'INDEX\((\'.+\'!\$[AB]\$\d+:\$[AB]\$\d+),MATCH\("(CT\d+)",\1,0\)\)', formula)
            if not match:
                raise AssertionError(f"Invalid recipe destination: {formula}")
            span, code = match.groups()
            title, addresses = span.rsplit("!", 1)
            ws = workbook[title[1:-1]]
            return next(c for row in ws[addresses] for c in row if c.value == code)
        title, address = location.rsplit("!", 1)
        return workbook[title.strip("'")][address]

    def assert_navigation(self, workbook):
        for ws in workbook:
            for row in ws:
                for cell in row:
                    if cell.hyperlink:
                        self.assertIsNone(cell.hyperlink.target)
                        self.assertTrue(cell.font.underline)
                        target = self.resolve(workbook, cell.hyperlink.location)
                        if ws.title == SHEETS[2]:
                            self.assertEqual((target.parent.title, target.value), (SHEETS[4], cell.value))
                        elif ws.title == SHEETS[4] and cell.column == 9 and cell.row >= 5:
                            self.assertEqual((target.parent.title, target.value), (SHEETS[2], ws.cell(cell.row, 1).value))
        for row in workbook[SHEETS[1]].iter_rows(min_row=5, min_col=3, max_col=5):
            self.assertTrue(all(c.hyperlink is None for c in row))  # No fake multi-link cells.

    def test_bidirectional_native_links_follow_identity_after_sort_filter_and_roundtrip(self):
        workbook = load_workbook(BytesIO(self.content))
        self.assert_navigation(workbook)
        from copy import copy
        for ws, first, key in ((workbook[SHEETS[2]], 4, 3), (workbook[SHEETS[4]], 5, 2)):
            rows = [[(c.value, copy(c._style), copy(c.hyperlink)) for c in row]
                    for row in ws.iter_rows(min_row=first)]
            rows.sort(key=lambda row: (str(row[key][0]), str(row[0][0])), reverse=True)
            for index, row in enumerate(rows, first):
                for col, (value, style, hyperlink) in enumerate(row, 1):
                    cell = ws.cell(index, col, value)
                    cell._style = style
                    cell.hyperlink = hyperlink
                    if hyperlink:
                        cell.hyperlink.ref = cell.coordinate
            ws.auto_filter.add_filter_column(0 if first == 5 else 1, ["CT025"])
            for row in range(first, ws.max_row + 1):
                ws.row_dimensions[row].hidden = ws.cell(row, 1 if first == 5 else 2).value != "CT025"
        self.assert_navigation(workbook)
        stream = BytesIO(); workbook.save(stream)
        self.assert_navigation(load_workbook(BytesIO(stream.getvalue())))

    def test_no_external_relationships_macros_charts_or_formula_cells(self):
        with ZipFile(BytesIO(self.content)) as archive:
            self.assertIsNone(archive.testzip())
            self.assertFalse(any("vba" in p.lower() or "externalLinks" in p or "/charts/" in p for p in archive.namelist()))
            for path in archive.namelist():
                if path.endswith(".rels"):
                    self.assertNotIn(b'TargetMode="External"', archive.read(path))
        self.assertTrue(all(c.data_type not in ("e", "f") for ws in self.wb for row in ws for c in row))

    def test_contract_rejects_missing_sheet_wrong_anchor_identity_and_changed_quantity(self):
        for kind in ("sheet", "anchor", "link", "quantity", "customer"):
            with self.subTest(kind=kind):
                workbook = load_workbook(BytesIO(self.content))
                if kind == "sheet":
                    del workbook[SHEETS[4]]
                elif kind == "anchor":
                    workbook.defined_names["BOM_Catalog_CT025"].attr_text = "'Missing'!A1"
                elif kind == "link":
                    workbook[SHEETS[2]]["B4"].hyperlink.location = "BOM_Catalog_CT080"
                elif kind == "quantity":
                    workbook[SHEETS[4]]["D5"] = 999
                else:
                    workbook[SHEETS[3]]["B5"] = "KH999"
                with self.assertRaises(ValueError):
                    verify_workbook(workbook, self.service.data, self.snapshot)

    def test_reversing_inference_order_does_not_change_customer_mapping(self):
        snapshot = deepcopy(self.snapshot)
        snapshot["evaluation"].reverse()
        workbook = load_workbook(BytesIO(build_workbook(self.service.data, self.service._model.metadata, snapshot)))
        self.assertEqual({r[7]: r[0] for r in table_rows(workbook[SHEETS[1]])}, customer_codes(self.service.data))
        verify_workbook(workbook, self.service.data, snapshot)
