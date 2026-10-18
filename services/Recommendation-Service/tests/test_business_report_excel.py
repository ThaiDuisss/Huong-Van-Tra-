"""Local workbook regression tests; no application startup or shared services."""
from datetime import date, datetime, timedelta
from io import BytesIO
import unittest
from zipfile import ZipFile

from openpyxl import load_workbook
from openpyxl.utils.cell import range_boundaries
from sqlalchemy import delete

from app.models.business_report import RecommendationSnapshot
from app.service.business_report_excel import DATE_FORMAT, INTEGER_FORMAT, TEA
from app.service.business_report_workbook import SHEETS, build_workbook, customer_code, period_bounds
from tests.test_business_reports import DbFixture, NOW, order, snapshot

HEADERS = [
    ["STT", "Sản phẩm", "Lượt AI gợi ý", "Số ly bán", "Lượt trùng mua"],
    ["Mã KH", "Ngày gợi ý", "AI gợi ý", "Khách mua sau đó", "Món trùng"],
    ["STT", "Sản phẩm", "Số khách được gợi ý", "Lượt gợi ý", "Số đơn có mua", "Số ly bán"],
    ["Mã lần gợi ý", "Mã KH", "Số món gợi ý", "Số món khách mua", "Số món trùng", "Kết quả"],
]


def table_values(ws, table_name):
    first_col, first_row, last_col, last_row = range_boundaries(ws.tables[table_name].ref)
    return list(ws.iter_rows(min_row=first_row + 1, max_row=last_row,
                             min_col=first_col, max_col=last_col, values_only=True))


class ExcelPresentationTests(DbFixture, unittest.TestCase):
    def build(self, records=(), as_of=NOW, from_date=date(2026, 10, 11), to_date=date(2026, 10, 12)):
        self.db.add_all(records)
        self.db.commit()
        data, evidence = build_workbook(self.db, from_date, to_date, as_of)
        with ZipFile(BytesIO(data)) as archive:
            self.assertIsNone(archive.testzip())
        return load_workbook(BytesIO(data)), evidence

    def test_empty_report_keeps_headers_real_dates_filters_and_zero_kpis(self):
        wb, evidence = self.build()
        self.assertEqual(wb.sheetnames, SHEETS)
        self.assertEqual(evidence["snapshots"], [])
        self.assertEqual(evidence["sales"], [])
        for index, ws in enumerate(wb):
            header = 9 if index == 0 else 3
            self.assertEqual([c.value for c in ws[header]], HEADERS[index])
            self.assertEqual(ws.auto_filter.ref, f"A{header}:{'E' if index < 2 else 'F'}{header}")
            self.assertEqual(ws.freeze_panes, None if index == 0 else "A4")
            self.assertEqual(ws.cell(header + 1, 1).value, "Chưa có dữ liệu")
            self.assertEqual(ws["A2"].value, datetime(2026, 10, 11))
            self.assertEqual(ws["C2"].value, datetime(2026, 10, 12))
            self.assertEqual(ws["A2"].number_format, '"Từ ngày: "' + DATE_FORMAT)
            self.assertEqual(ws["C2"].number_format, '"Đến ngày: "' + DATE_FORMAT)
            self.assertEqual(ws["A2"].data_type, "d")
            self.assertEqual(ws["C2"].data_type, "d")
            self.assertIn("12/10/2026 17:00:00", ws["A1"].comment.text)
            self.assertIn("Asia/Ho_Chi_Minh", ws["A1"].comment.text)
            self.assertTrue(ws.sheet_view.showGridLines)
            self.assertIsNone(ws.sheet_properties.tabColor)
            self.assertEqual(ws.sheet_state, "visible")
            self.assertTrue(wb.views[0].showSheetTabs)
            if index:
                last_column = "E" if index == 1 else "F"
                self.assertEqual({str(r) for r in ws.merged_cells.ranges},
                                 {f"A1:{last_column}1", "A2:B2", f"C2:{last_column}2", f"A4:{last_column}4"})
            self.assertLess(ws.max_row, 40)
            self.assertEqual(len(ws._charts), 0)
        self.assertEqual([wb.worksheets[0][c].value for c in ("B4", "B5", "B6", "B7")], [0] * 4)

    def test_compact_kpi_table_headers_and_integer_alignment(self):
        wb, _ = self.build([snapshot(), order()])
        overview = wb.worksheets[0]
        self.assertEqual(overview["A1"].value, SHEETS[0])
        self.assertEqual([overview[c].value for c in ("B4", "B5", "B6", "B7")], [1, 2, 6, 1])
        for address in ("B4", "B5", "B6", "B7"):
            cell = overview[address]
            self.assertEqual(cell.number_format, INTEGER_FORMAT)
            self.assertIsNone(cell.fill.patternType)
            self.assertEqual(cell.font.name, "Calibri")
            self.assertEqual(cell.font.sz, 11)
        self.assertEqual([overview.cell(3, c).value for c in (1, 2)], ["Chỉ tiêu", "Giá trị"])
        self.assertEqual([overview.cell(r, 1).value for r in range(4, 8)], [
            "Khách hàng được gợi ý", "Lượt gợi ý sản phẩm", "Số ly bán thực tế", "Lượt gợi ý trùng mua"])
        self.assertTrue(all(overview.cell(r, c).value is None for r in range(3, 8) for c in range(3, 6)))
        for index, ws in enumerate(wb):
            header = 9 if index == 0 else 3
            for cell in ws[header]:
                self.assertEqual(cell.fill.fgColor.rgb[-6:], TEA)
                self.assertEqual(cell.font.color.rgb[-6:], "FFFFFF")
                self.assertTrue(cell.font.bold)
                self.assertTrue(cell.alignment.wrap_text)
            self.assertIsNone(ws.cell(header + 1, 2).fill.patternType)
            self.assertEqual(ws.cell(header + 1, 2).border.bottom.color.rgb[-6:], "DCE5DF")
            table = next(iter(ws.tables.values()))
            self.assertEqual(table.autoFilter.ref, table.ref)
            self.assertIsNone(ws.auto_filter.ref)
            for column in ws.column_dimensions.values():
                self.assertLessEqual(column.width, 50)
        products = wb.worksheets[2]
        self.assertEqual(products["A4"].alignment.horizontal, "center")
        self.assertEqual(products["B4"].alignment.horizontal, "left")
        self.assertIsNone(products["B5"].fill.patternType)
        for row in products.iter_rows(min_row=4, min_col=3, max_col=6):
            for cell in row:
                self.assertIsInstance(cell.value, int)
                self.assertEqual(cell.number_format, INTEGER_FORMAT)
                self.assertEqual(cell.alignment.horizontal, "right")

    def test_populated_sheets_keep_gridlines_plain_tabs_and_default_surroundings(self):
        wb, _ = self.build([snapshot(), order()])
        self.assertEqual(wb.sheetnames, SHEETS)
        self.assertTrue(wb.views[0].showSheetTabs)
        for index, ws in enumerate(wb):
            self.assertTrue(ws.sheet_view.showGridLines)
            self.assertIsNone(ws.sheet_properties.tabColor)
            self.assertEqual(ws.sheet_state, "visible")
            self.assertEqual(ws.freeze_panes, None if index == 0 else "A4")
            if index:
                self.assertEqual(ws.sheet_view.pane.ySplit, 3)
                self.assertIsNone(ws.sheet_view.pane.xSplit)
                self.assertTrue(all(r.max_row < 3 for r in ws.merged_cells.ranges))
                table = next(iter(ws.tables.values()))
                self.assertEqual(table.ref, f"A3:{'E' if index == 1 else 'F'}{ws.max_row}")
            for table in ws.tables.values():
                self.assertFalse(table.tableStyleInfo.showRowStripes)
                self.assertFalse(table.tableStyleInfo.showColumnStripes)
            outside = ws.cell(1, ws.max_column + 1)
            self.assertIsNone(outside.value)
            self.assertFalse(outside.has_style)
            self.assertIsNone(outside.fill.patternType)
            self.assertIsNone(outside.border.bottom.style)
        for ws in wb.worksheets[1:]:
            for row in ws.iter_rows(min_row=4, max_col=5 if ws == wb.worksheets[1] else 6):
                for cell in row:
                    self.assertIsNone(cell.fill.patternType)
                    self.assertEqual(cell.font.name, "Calibri")
                    self.assertEqual(cell.font.sz, 11)
                    self.assertEqual(cell.font.color.rgb[-6:], "000000")
                    self.assertEqual(cell.border.bottom.style, "thin")

    def test_dates_cross_midnight_but_attribution_timezone_and_evidence_are_unchanged(self):
        utc = datetime(2026, 10, 12, 17, 45, 30)
        s = snapshot(when=utc - timedelta(minutes=30))
        wb, evidence = self.build([s], as_of=utc, from_date=date(2026, 10, 13), to_date=date(2026, 10, 13))
        for ws in wb:
            self.assertIn("13/10/2026 00:45:30", ws["A1"].comment.text)
        date_cell = wb.worksheets[1]["B4"]
        self.assertEqual(date_cell.value, datetime(2026, 10, 13, 0, 15, 30))
        self.assertEqual(date_cell.data_type, "d")
        self.assertEqual(date_cell.number_format, DATE_FORMAT)
        self.assertIsNone(date_cell.value.tzinfo)
        self.assertEqual(evidence["as_of"], utc.isoformat())
        self.assertEqual(evidence["period_timezone"], "Asia/Bangkok")
        self.assertEqual(evidence["policy"], "next-order-nearest-prior-v1")
        self.assertEqual(evidence["snapshots"][0]["generated_at"], s.generated_at.isoformat())
        self.assertEqual(period_bounds(date(2026, 10, 13), date(2026, 10, 13))[0], datetime(2026, 10, 12, 17))

    def test_business_notes_follow_the_product_table_and_remain_complete(self):
        wb, _ = self.build([snapshot(), order()])
        ws = wb.worksheets[0]
        notes = [(row[0].row, row[0].value) for row in ws if isinstance(row[0].value, str)]
        note_header = next(row for row, value in notes if value == "GHI CHÚ VÀ PHƯƠNG PHÁP THỐNG KÊ")
        self.assertGreater(note_header, range_boundaries(ws.tables["Overview"].ref)[3])
        explanations = [value for row, value in notes if row > note_header]
        self.assertEqual(len(explanations), 11)
        for phrase in ("không chứng minh tác động", "không phải lượt xem", "không trừ RETURN", "tối đa một lần gợi ý",
                       "mặc định 7 ngày", "không được tính là gợi ý sai", "không suy ra không mua", "mã ẩn danh ổn định"):
            self.assertTrue(any(phrase in note for note in explanations), phrase)

    def test_statuses_keep_unknown_counts_and_use_plain_black_text(self):
        rows = [snapshot("old", NOW - timedelta(hours=3)), snapshot("winner", NOW - timedelta(hours=2)),
                snapshot("pending", customer="other"), snapshot("expired", NOW - timedelta(days=9), customer="expired"),
                snapshot("no-overlap", customer="no-overlap"), order(), order("other-order", customer="no-overlap", products=(("x", 1),))]
        wb, evidence = self.build(rows, from_date=date(2026, 10, 1))
        ws = wb.worksheets[3]
        data = {r[0]: r for r in table_values(ws, "Matches")}
        self.assertEqual(data["pending"][3:5], (None, None))
        self.assertEqual(data["old"][3:5], (None, None))
        self.assertEqual(data["expired"][3:5], (0, 0))
        self.assertEqual(data["winner"][5], "Trùng 1 món")
        self.assertEqual(data["no-overlap"][5], "Không trùng")
        self.assertEqual({e["status"] for e in evidence["snapshots"]},
                         {"SUPERSEDED", "MATCHED", "NOT_YET_EVALUATED", "NO_PURCHASE", "NO_OVERLAP"})
        self.assertEqual(len(ws.conditional_formatting), 0)
        for row in ws.iter_rows(min_row=4):
            for cell in row:
                self.assertIsNone(cell.fill.patternType)
                self.assertEqual(cell.font.color.rgb[-6:], "000000")
        self.assertEqual(ws["A4"].number_format, "@")
        self.assertEqual(ws["B4"].number_format, "@")

    def test_long_vietnamese_names_have_height_for_every_line(self):
        s = snapshot(products=("a", "b", "c", "d", "e"))
        for item in s.response["items"]:
            item["name"] = "Trà Ô Long Hoa Nhài Hương Vân Trà – ít đường, nhiều đá, thêm trân châu trắng " * 3
        wb, _ = self.build([s])
        ws = wb.worksheets[1]
        text = "".join(row[2] or "" for row in table_values(ws, "Customers"))
        self.assertEqual(text, "Khám phá\n" + "\n".join(i["name"] for i in s.response["items"]))
        self.assertTrue(all(20 <= d.height <= 409.5 for r, d in ws.row_dimensions.items() if r >= 4))
        self.assertTrue(ws["C4"].alignment.wrap_text)
        self.assertEqual(ws["A4"].value, customer_code(s.customer_id))

    def test_content_beyond_excel_cell_limit_continues_without_loss_or_duplicate_counts(self):
        name = "Trà sen Việt Nam – Hương Vân Trà\n" * 1100
        self.assertGreater(len(name), 32767)
        s = snapshot(products=("a",))
        s.response["items"][0]["name"] = name
        wb, evidence = self.build([s, order(products=(("a", 2),))])
        overview = table_values(wb.worksheets[0], "Overview")
        self.assertEqual("".join(r[1] or "" for r in overview), name)
        self.assertEqual(sum(r[2] or 0 for r in overview), 1)
        self.assertEqual(sum(r[3] or 0 for r in overview), 2)
        self.assertEqual(sum(r[4] or 0 for r in overview), 1)
        details = table_values(wb.worksheets[1], "Customers")
        self.assertEqual("".join(r[2] or "" for r in details), "Khám phá\n" + name)
        self.assertEqual("".join(r[3] or "" for r in details), name + " × 2")
        self.assertEqual("".join(r[4] or "" for r in details), name)
        self.assertEqual(len(evidence["snapshots"]), 1)
        self.assertEqual(len(evidence["sales"]), 1)
        for ws in wb:
            self.assertTrue(all(d.height <= 409.5 for d in ws.row_dimensions.values()))

    def test_wide_uppercase_characters_get_enough_height_without_changing_text(self):
        name = "TRÀ " + "W" * 250
        s = snapshot(products=("a",))
        s.response["items"][0]["name"] = name
        wb, _ = self.build([s])
        self.assertEqual(wb.worksheets[2]["B4"].value, name)
        self.assertGreaterEqual(wb.worksheets[2].row_dimensions[4].height, 130)
        self.assertTrue(wb.worksheets[2]["B4"].alignment.wrap_text)

    def test_untrusted_text_and_numeric_looking_ids_remain_literal_after_roundtrip(self):
        names = ['=HYPERLINK("https://invalid","x")', "+SUM(1,2)", "-2+3", "@SUM(1)", "\t=1+1"]
        s = snapshot("0000123456789012345678901234567890", products=("a", "b", "c", "d", "e"))
        for item, name in zip(s.response["items"], names):
            item["name"] = name
        wb, _ = self.build([s])
        self.assertEqual(wb.worksheets[3]["A4"].value, s.id)
        self.assertEqual(wb.worksheets[3]["A4"].data_type, "s")
        self.assertEqual(wb.worksheets[3]["A4"].number_format, "@")
        product_names = {r[1] for r in table_values(wb.worksheets[2], "Products")}
        self.assertEqual(product_names, set(names))
        self.assertFalse(any(c.data_type == "f" for ws in wb for row in ws for c in row))

    def test_print_areas_repeat_only_table_header_and_use_appropriate_a4_orientation(self):
        wb, _ = self.build([snapshot(), order()])
        for index, ws in enumerate(wb):
            header = 9 if index == 0 else 3
            self.assertEqual(ws.print_title_rows, f"${header}:${header}")
            self.assertIn(f"$A$1:${'E' if index < 2 else 'F'}${ws.max_row}", str(ws.print_area))
            self.assertEqual(ws.page_setup.orientation, "portrait" if index == 0 else "landscape")
            self.assertEqual(int(ws.page_setup.paperSize), int(ws.PAPERSIZE_A4))
            self.assertEqual(ws.page_setup.fitToWidth, 1)
            self.assertEqual(ws.page_setup.fitToHeight, 0)
            self.assertIn("&P", ws.oddFooter.right.text)

    def test_zero_ten_hundred_and_ten_thousand_records_preserve_counts_and_filters(self):
        for count in (0, 10, 100, 10000):
            with self.subTest(count=count):
                self.db.execute(delete(RecommendationSnapshot))
                self.db.commit()
                wb, evidence = self.build([snapshot(f"snapshot-{i:05d}", customer=f"customer-{i:05d}") for i in range(count)])
                self.assertEqual(wb.sheetnames, SHEETS)
                self.assertEqual(len(evidence["snapshots"]), count)
                self.assertEqual(wb.worksheets[0]["B4"].value, count)
                self.assertEqual(wb.worksheets[0]["B5"].value, count * 2)
                self.assertEqual(wb.worksheets[0]["B6"].value, 0)
                if count:
                    for index, table in ((1, "Customers"), (3, "Matches")):
                        ws = wb.worksheets[index]
                        self.assertEqual(len(table_values(ws, table)), count)
                        self.assertEqual(ws.tables[table].autoFilter.ref, ws.tables[table].ref)
                        self.assertIsNone(ws.auto_filter.ref)
                        self.assertEqual(ws.max_row, count + 3)
                    self.assertEqual(wb.worksheets[1].max_column, 5)
                    self.assertEqual(wb.worksheets[3].max_column, 6)


if __name__ == "__main__":
    unittest.main()
