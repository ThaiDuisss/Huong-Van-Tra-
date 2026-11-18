"""BOM business export; no Product runtime imports or model changes."""
from datetime import datetime, timedelta
from decimal import Decimal
from io import BytesIO
import math

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter
from openpyxl.workbook.defined_name import DefinedName
from openpyxl.worksheet.hyperlink import Hyperlink

from app.ingredient_demo.bom_report import PROTOCOL
from app.ingredient_demo.bom_display import customer_codes, recipe_codes, recipe_names, recipe_components
from app.ingredient_poc.synthetic import END, T2


SHEETS = ["TỔNG QUAN BÁO CÁO", "CHI TIẾT THEO KHÁCH HÀNG", "THỐNG KÊ BOM", "DANH MỤC CÔNG THỨC",
          "TRA CỨU THEO KHÁCH HÀNG"]
WORKBOOK_SCHEMA = "bom-period-customer-lookup-v5"
WARNING = "Dữ liệu mô phỏng phục vụ nghiên cứu, không phải doanh số bán hàng thực tế."
LOOKUP_GUIDE = "Bấm Tra cứu công thức để xem các mã được gợi ý cho khách này; bấm mã CT để mở thành phần."
HEADER_ROWS = (3, 4, 3, 4, 3)
CATALOG_HEADERS = ["Mã công thức", "Tên công thức", "Nguyên liệu (tên nguồn hoặc mã)",
                   "Định lượng gốc", "Đơn vị", "Mã công thức gốc (recipe_id)",
                   "Phiên bản công thức nguồn", "Mã kiểm chứng công thức (hash)", "Về THỐNG KÊ BOM"]
PERCENT = "0.00%"
DATE = "dd/mm/yyyy"
GREEN = "245C47"
HEADERS = [
    ["Chỉ tiêu", "Giá trị", "Diễn giải"],
    ["Mã khách hàng", "Ngày gợi ý", "Công thức AI đề xuất", "Công thức khách mua", "Công thức trùng nhau", "Tỷ lệ trùng mua (%)",
     "Tra cứu công thức", "Mã khách hàng gốc (customer_id)"],
    ["STT", "Mã công thức", "Số khách được AI gợi ý", "Số lượt được AI gợi ý", "Số đơn hàng có mua công thức",
     "Số lượt gợi ý trùng mua", "Tỷ lệ gợi ý trùng mua (%)", "Số lượng công thức được mua"],
    CATALOG_HEADERS,
    ["Mã khách hàng", "Gợi ý 1", "Gợi ý 2", "Gợi ý 3", "Gợi ý 4", "Gợi ý 5", "Mã khách hàng gốc (customer_id)"],
]


def recipe_labels(data):
    """Short stable codes; matching always uses full recipe IDs, never these labels."""
    return recipe_codes(data)


def table_rows(ws):
    """Only the main filtered table, excluding titles and notes."""
    from openpyxl.utils.cell import range_boundaries
    left, top, right, bottom = range_boundaries(ws.auto_filter.ref)
    return ws.iter_rows(min_row=top + 1, max_row=bottom, min_col=left, max_col=right, values_only=True)


def anchor_formula(code, ws, column, first, last):
    span = f"'{ws.title}'!${column}${first}:${column}${last}"
    return f'INDEX({span},MATCH("{code}",{span},0))'


def label_window(protocol):
    start = datetime.fromisoformat(protocol["label_start_inclusive"])
    end = datetime.fromisoformat(protocol["label_end_exclusive"])
    if not T2 <= start < end <= END:
        raise ValueError("Incompatible BOM report period")
    return start, end


def period_label(start, end):
    return f"Kỳ mua được đối chiếu: {start:%d/%m/%Y} – {end - timedelta(days=1):%d/%m/%Y} (UTC). Dữ liệu mô phỏng."


def verify_workbook(workbook, data, snapshot):
    """Verify the saved business contract against its pinned source, offline."""
    def require(condition):
        if not condition:
            raise ValueError("BOM workbook contract mismatch")

    require(workbook.sheetnames == SHEETS)
    tables = [list(table_rows(ws)) for ws in workbook]
    for index, ws in enumerate(workbook):
        require([c.value for c in ws[HEADER_ROWS[index]]][:len(HEADERS[index])] == HEADERS[index])
        require(ws.freeze_panes is None and ws.sheet_view.showGridLines)
        for row in ws:
            for cell in row:
                require(cell.data_type not in ("f", "e"))
                if cell.hyperlink:
                    require(cell.hyperlink.target is None and bool(cell.hyperlink.location))
    overview, detail, stats, catalog, lookup = workbook.worksheets
    require(overview["A2"].value == period_label(*label_window(snapshot["protocol"])))
    summary = snapshot["summary"]
    require(overview["A14"].value == WARNING)
    require(any("source_kind=SYNTHETIC; research_only=true; production_approved=false" in str(row[0])
                for row in overview.values))
    require(overview["B5"].value == summary["recommendations"] and overview["B6"].value == summary["matches"])
    require(len(tables[1]) == len(snapshot["evaluation"]))
    require(len(tables[2]) == len(data.recipes) == stats.max_row - HEADER_ROWS[2])
    codes, customers, names = recipe_codes(data), customer_codes(data), recipe_names(data)
    require({r[7]: r[0] for r in tables[1]} == customers)
    details = {r[7]: r for r in tables[1]}
    for record in snapshot["evaluation"]:
        require(details[record["customer_id"]][0] == customers[record["customer_id"]])
        for column, key in ((2, "recommended"), (3, "purchased"), (4, "matched")):
            require(details[record["customer_id"]][column] == ("\n".join(codes[rid] for rid in record[key]) or "Không có"))
        require(details[record["customer_id"]][5] == (record["match_rate"] if record["match_rate"] is not None else "Không có gợi ý"))
    require(sum(r[3] for r in tables[2]) == summary["recommendations"])
    require(sum(r[5] for r in tables[2]) == summary["matches"])
    expected = []
    for rid in sorted(data.recipes):
        recipe = data.recipes[rid]
        for item in recipe_components(data, rid):
            expected.append((codes[rid], names[rid], item["name"], item["quantity"], item["unit"],
                             rid, recipe.get("recipe_version"), recipe.get("recipe_hash"), "Về THỐNG KÊ BOM"))
    actual = [(*r[:3], Decimal(str(r[3])) if r[3] is not None else None, *r[4:]) for r in tables[3]]
    require(actual == expected)
    require(catalog.max_row == len(expected) + HEADER_ROWS[3])
    for kind, ws, column, first in (("Catalog", catalog, "A", 5), ("Stats", stats, "B", 4)):
        require(set(ws.cell(row, column=1 if kind == "Catalog" else 2).value
                    for row in range(first, ws.max_row + 1)) == set(codes.values()))
        for code in codes.values():
            name = workbook.defined_names.get(f"BOM_{kind}_{code}")
            require(name is not None and name.attr_text == anchor_formula(code, ws, column, first, ws.max_row))
    for row in range(4, stats.max_row + 1):
        cell = stats.cell(row, 2)
        require(cell.hyperlink is not None and cell.hyperlink.location == f"BOM_Catalog_{cell.value}")
    for row in range(5, catalog.max_row + 1):
        cell = catalog.cell(row, 9)
        require(cell.hyperlink is not None and cell.hyperlink.location == f"BOM_Stats_{catalog.cell(row, 1).value}")
    lookup_rows = {lookup.cell(row, 7).value: row for row in range(4, lookup.max_row + 1)}
    require(len(tables[4]) == len(snapshot["evaluation"]) and set(lookup_rows) == set(details))
    for row in range(5, detail.max_row + 1):
        code = detail.cell(row, 1).value
        name = f"BOM_Customer_{code}"
        link = detail.cell(row, 7).hyperlink
        anchor = workbook.defined_names.get(name)
        require(link is not None and link.location == name and anchor is not None
                and anchor.attr_text == anchor_formula(code, lookup, "A", 4, lookup.max_row))
    for record in snapshot["evaluation"]:
        customer = record["customer_id"]
        row = lookup_rows[customer]
        require(lookup.cell(row, 1).value == customers[customer])
        suggestions = [codes[rid] for rid in record["recommended"]]
        for index in range(5):
            cell = lookup.cell(row, index + 2)
            if index < len(suggestions):
                code = suggestions[index]
                require(cell.value == code and cell.hyperlink is not None and cell.hyperlink.location == f"BOM_Catalog_{code}")
            else:
                require(cell.value == ("Không có gợi ý" if index == 0 else None) and cell.hyperlink is None)
    return {"sheet_data_rows": {ws.title: len(rows) for ws, rows in zip(workbook, tables)},
            "recipe_count": len(codes), "ingredient_lines": sum(r[3] is not None for r in actual),
            "recipe_hyperlinks": len(tables[2]), "return_hyperlinks": len(actual),
            "customer_lookup_hyperlinks": len(tables[1]), "suggestion_hyperlinks": summary["recommendations"]}


def build_workbook(data, metadata, snapshot):
    protocol, summary, records = snapshot["protocol"], snapshot["summary"], snapshot["evaluation"]
    start, end = label_window(protocol)
    if (protocol["name"] != PROTOCOL or protocol["as_of"] != T2.isoformat()
            or {r["customer_id"] for r in records} != set(data.customers)
            or len(records) != len(data.customers)):
        raise ValueError("Incomplete BOM report or incompatible protocol")
    labels = recipe_labels(data)
    customers = customer_codes(data)
    names = recipe_names(data)
    workbook = Workbook()
    workbook.remove(workbook.active)
    workbook.views[0].showSheetTabs = True
    workbook.properties.title = "BÁO CÁO KẾT QUẢ GỢI Ý CÔNG THỨC TRÀ"
    workbook.properties.description = WARNING
    period = period_label(start, end)

    def sheet(index, widths):
        ws = workbook.create_sheet(SHEETS[index])
        end = get_column_letter(len(widths))
        ws.merge_cells(f"A1:{end}1")
        ws.merge_cells(f"A2:{end}2")
        ws["A1"] = workbook.properties.title if index == 0 else SHEETS[index]
        ws["A2"] = period
        ws.row_dimensions[1].height = 26
        ws.row_dimensions[2].height = 28
        ws.freeze_panes = None
        ws.sheet_view.showGridLines = True
        for n, width in enumerate(widths, 1):
            ws.column_dimensions[get_column_letter(n)].width = width
        return ws

    def header(ws, row, values):
        for n, value in enumerate(values, 1):
            cell = ws.cell(row, n, value)
            cell.fill = PatternFill("solid", fgColor=GREEN)
            cell.font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
            cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        ws.row_dimensions[row].height = 44

    def finish(ws, filter_row=3, filter_end=None, extra_headers=()):
        for row in ws:
            for cell in row:
                is_header = cell.row in (filter_row, *extra_headers)
                if not is_header:
                    cell.font = Font(name="Calibri", size=11)
                    cell.alignment = Alignment(vertical="top", wrap_text=True,
                                               horizontal="right" if isinstance(cell.value, (int, float, Decimal)) else "left")
                if isinstance(cell.value, str):
                    cell.data_type = "s"  # Artifact text cannot become a spreadsheet formula.
                if isinstance(cell.value, datetime):
                    cell.number_format = DATE
                elif isinstance(cell.value, (int, float, Decimal)) and cell.number_format == "General":
                    cell.number_format = "#,##0"
        ws["A1"].font = Font(name="Calibri", size=13, bold=True, color=GREEN)
        ws["A1"].alignment = Alignment(vertical="center")
        ws["A2"].alignment = Alignment(vertical="center", wrap_text=True)
        ws.auto_filter.ref = f"A{filter_row}:{get_column_letter(ws.max_column)}{filter_end or ws.max_row}"
        ws.print_title_rows = f"1:{filter_row}"
        ws.print_options.horizontalCentered = True
        ws.sheet_properties.pageSetUpPr.fitToPage = True
        ws.page_setup.orientation = "landscape"
        ws.page_setup.paperSize = ws.PAPERSIZE_A3
        ws.page_setup.fitToWidth = 1
        ws.page_setup.fitToHeight = 0
        ws.print_area = ws.dimensions

    def link(cell, location):
        cell.hyperlink = Hyperlink(ref=cell.coordinate, location=location)
        cell.font = Font(name="Calibri", size=11, color=GREEN, underline="single")

    def recipe_anchor(kind, code, ws, column, first, last):
        # INDEX returns a reference. MATCH follows recipe identity after whole-table
        # sorting in either sheet; neither direction assumes the old row position.
        name = f"BOM_{kind}_{code}"
        workbook.defined_names.add(DefinedName(name, attr_text=anchor_formula(code, ws, column, first, last)))
        return name

    overview = sheet(0, [55, 29, 20, 22, 27])
    header(overview, 3, HEADERS[0])
    overview.merge_cells("C3:E3")
    kpis = [
        ("Số khách hàng được đánh giá", summary["customers"], "Mỗi khách được tính một lần, kể cả khách không mua công thức trong kỳ."),
        ("Tổng số công thức AI đã gợi ý", summary["recommendations"], "Cộng số công thức thực sự được đề xuất cho từng khách; tối đa 5 mỗi khách."),
        ("Số lượt gợi ý trùng với công thức khách mua", summary["matches"], "Cùng một công thức chỉ tính một lần cho mỗi khách, dù khách mua lặp lại."),
        ("Tỷ lệ gợi ý trùng với lựa chọn mua (%)", summary["match_rate"], "Số lượt gợi ý trùng mua / tổng lượt gợi ý."),
        ("Tổng số công thức có trong bộ dữ liệu", summary["dataset_recipes"], "Mỗi công thức gốc có mã CT riêng; không gộp công thức giống thành phần."),
        ("Số công thức đã được AI gợi ý", summary["recommended_recipes"], "Số công thức khác nhau đã xuất hiện trong gợi ý."),
        ("Tỷ lệ công thức được AI sử dụng (%)", summary["coverage"], "Số công thức đã được gợi ý / số công thức đủ điều kiện tại ngày gợi ý."),
        ("Số công thức đủ điều kiện tại ngày gợi ý", summary["eligible_recipes"], "Mẫu số của tỷ lệ công thức được AI sử dụng; có thể khác tổng bộ dữ liệu."),
        ("Số khách mua công thức hợp lệ trong kỳ", summary["customers_with_bom_labels"], "Chỉ tính công thức đủ điều kiện để đối chiếu; không suy từ việc mua sản phẩm."),
    ]
    for row, (label, value, definition) in enumerate(kpis, 4):
        overview.cell(row, 1, label)
        overview.cell(row, 2, value if value is not None else "Không có mẫu số")
        overview.cell(row, 3, definition)
        overview.merge_cells(start_row=row, start_column=3, end_row=row, end_column=5)
        overview.row_dimensions[row].height = 34
        if value is not None and row in (7, 10):
            overview.cell(row, 2).number_format = PERCENT
    notes = [WARNING,
             "AI đề xuất tối đa 5 công thức cho mỗi khách hàng. Sau đó, hệ thống so sánh các công thức được đề xuất với công thức khách mua trong kỳ đánh giá.",
             "Tỷ lệ trùng mua = Số lượt gợi ý trùng với công thức khách mua / Tổng số lượt gợi ý."]
    for row, note in enumerate(notes, 14):
        overview.cell(row, 1, note)
        overview.merge_cells(start_row=row, start_column=1, end_row=row, end_column=5)
        overview.row_dimensions[row].height = 38

    def count_text(value):
        return f"{value:,}".replace(",", ".")

    rate_text = (f"{summary['match_rate']:.2%}".replace(".", ",")
                 if summary["match_rate"] is not None else "chưa có tỷ lệ do không có gợi ý")
    overview["A18"] = (f"Trong kỳ đánh giá, AI đã đề xuất {count_text(summary['recommendations'])} công thức "
                       f"cho {count_text(summary['customers'])} khách hàng. Có {count_text(summary['matches'])} lượt "
                       f"đề xuất trùng với công thức khách mua, tương đương {rate_text}.")
    overview.merge_cells("A18:E18")
    overview.row_dimensions[18].height = 38
    for row, index, text in ((20, 1, "Xem kết quả theo khách hàng"),
                             (21, 2, "Xem hiệu quả từng công thức"),
                             (22, 3, "Xem thành phần và định lượng")):
        overview.cell(row, 1, f"{text} tại sheet {SHEETS[index]}.")
        overview.merge_cells(start_row=row, start_column=1, end_row=row, end_column=5)
        overview.row_dimensions[row].height = 28
    provenance_row = 25
    overview.cell(provenance_row, 1, "GHI CHÚ NGUỒN VÀ PHƯƠNG PHÁP")
    overview.merge_cells(start_row=provenance_row, start_column=1, end_row=provenance_row, end_column=5)
    overview.row_dimensions[provenance_row].height = 26
    technical_notes = [
        f"source_kind=SYNTHETIC; research_only=true; production_approved=false. Model: {metadata['model_version']}. Dataset: {data.id}.",
        f"Protocol: {PROTOCOL}; CUSTOM_BOM / REPEAT / Top-5; as_of={T2.isoformat()}. "
        "Giữ nguyên gợi ý tại mốc này; chỉ các lượt mua BOM trong kỳ UTC đã chọn được tính. Đối chiếu bằng recipe_id.",
        "Thành phần là định lượng gốc trong công thức mô phỏng; không phải lượng đóng gói hoặc lượng khách đã uống. Không cộng hao hụt, không đổi đơn vị. Tên nguyên liệu giữ nguyên nguồn, không phải tên thương mại đã phê duyệt.",
        "Tín hiệu từ mua sản phẩm là suy diễn, không phải ảnh chụp công thức thực tế. Đối chiếu này chỉ dùng lượt mua công thức, không dùng cầu nối sản phẩm để tính trùng.",
        "Số đơn tính riêng từng công thức nên một đơn có thể xuất hiện ở nhiều dòng. Số lượng mua theo đơn vị nguồn từng công thức; không phải gram/ml đã uống. "
        f"Các đơn vị mua trong kỳ: {', '.join(sorted({r['sale_unit'] for r in snapshot['recipes'] if r['sale_unit']})) or 'chưa có'}. "
        f"Loại {summary['excluded_unavailable_label_rows']} dòng mua khỏi đối chiếu vì công thức chưa đủ điều kiện tại ngày gợi ý."]
    for row, note in enumerate(technical_notes, provenance_row + 1):
        overview.cell(row, 1, note)
        overview.merge_cells(start_row=row, start_column=1, end_row=row, end_column=5)
        overview.row_dimensions[row].height = 46
    finish(overview, 3, 12)
    overview["A14"].font = Font(name="Calibri", size=11, bold=True, color=GREEN)
    overview.cell(provenance_row, 1).font = Font(name="Calibri", size=11, bold=True, color=GREEN)
    for row, index in ((20, 1), (21, 2), (22, 3)):
        link(overview.cell(row, 1), f"'{SHEETS[index]}'!A1")

    detail = sheet(1, [18, 17, 27, 27, 27, 23, 28, 42])
    detail.merge_cells("A3:H3")
    detail["A3"] = LOOKUP_GUIDE
    detail.row_dimensions[3].height = 26
    header(detail, 4, HEADERS[1])
    stats = sheet(2, [12, 30, 30, 22, 23, 43, 25, 24])
    header(stats, 3, HEADERS[2])
    for r in records:
        texts = ["\n".join(labels[rid] for rid in r[key]) or "Không có" for key in ("recommended", "purchased", "matched")]
        ratio = r["match_rate"] if r["match_rate"] is not None else "Không có gợi ý"
        detail.append([customers[r["customer_id"]], T2.replace(tzinfo=None), *texts, ratio,
                       "Tra cứu công thức", r["customer_id"]])
        lines = max(sum(max(1, math.ceil(len(line) / 42)) for line in text.splitlines()) for text in texts)
        detail.row_dimensions[detail.max_row].height = max(30, 15 * lines + 6)
        if r["match_rate"] is not None:
            detail.cell(detail.max_row, 6).number_format = PERCENT
    finish(detail, 4)

    for n, r in enumerate(snapshot["recipes"], 1):
        stats.append([n, labels[r["recipe_id"]], r["suggested_customers"], r["recommendation_count"],
                      r["purchase_orders"], r["matched_count"],
                      r["match_rate"] if r["match_rate"] is not None else "Chưa được gợi ý", r["sold_quantity"]])
        stats.row_dimensions[stats.max_row].height = 34
        stats.cell(stats.max_row, 8).number_format = "#,##0.########"
        if r["match_rate"] is not None:
            stats.cell(stats.max_row, 7).number_format = PERCENT
    stats_end = stats.max_row
    finish(stats, 3, stats_end)
    catalog = sheet(3, [18, 26, 36, 20, 12, 42, 20, 40, 28])
    catalog["A1"] = "DANH MỤC CÔNG THỨC VÀ THÀNH PHẦN"
    catalog["A2"] = "Tra cứu nguyên liệu và định lượng của từng công thức được hệ thống AI sử dụng trong nghiên cứu."
    catalog.merge_cells("A3:H3")
    catalog["A3"] = "Lọc mã CT hoặc nguyên liệu / Ctrl+F để tìm. Định lượng và đơn vị giữ nguyên nguồn. Nếu đã lọc, xóa bộ lọc trước khi đi theo liên kết tới dòng đang bị ẩn."
    catalog.row_dimensions[3].height = 36
    catalog["I3"] = "Về Tổng quan"
    header(catalog, 4, CATALOG_HEADERS)
    for rid in sorted(data.recipes):
        for item in recipe_components(data, rid):
            recipe = data.recipes[rid]
            catalog.append([labels[rid], names[rid], item["name"], item["quantity"], item["unit"], rid,
                            recipe.get("recipe_version"), recipe.get("recipe_hash"), "Về THỐNG KÊ BOM"])
            catalog.row_dimensions[catalog.max_row].height = max(40, 15 * math.ceil(len(item["name"]) / 32) + 6)
            catalog.cell(catalog.max_row, 4).number_format = "#,##0.########"
    finish(catalog, 4)
    link(catalog["I3"], f"'{SHEETS[0]}'!A1")
    for code in labels.values():
        recipe_anchor("Catalog", code, catalog, "A", 5, catalog.max_row)
        recipe_anchor("Stats", code, stats, "B", 4, stats_end)
    for row, r in enumerate(snapshot["recipes"], 4):
        link(stats.cell(row, 2), f"BOM_Catalog_{labels[r['recipe_id']]}")
    for row in range(5, catalog.max_row + 1):
        link(catalog.cell(row, 9), f"BOM_Stats_{catalog.cell(row, 1).value}")

    lookup = sheet(4, [19, 22, 15, 15, 15, 15, 43])
    lookup.merge_cells("A2:G2")
    lookup["A2"] = "Bấm từng mã CT để xem đầy đủ nguyên liệu, định lượng và đơn vị. Nếu dòng đích bị ẩn, hãy xóa bộ lọc danh mục."
    header(lookup, 3, HEADERS[4])
    for record in records:
        customer_id = record["customer_id"]
        suggestions = [labels[rid] for rid in record["recommended"]] or ["Không có gợi ý"]
        lookup.append([customers[customer_id], *suggestions, *([None] * (5 - len(suggestions))), customer_id])
        lookup.row_dimensions[lookup.max_row].height = 30
    finish(lookup)
    for index, record in enumerate(records):
        code = customers[record["customer_id"]]
        name = f"BOM_Customer_{code}"
        workbook.defined_names.add(DefinedName(name, attr_text=anchor_formula(code, lookup, "A", 4, lookup.max_row)))
        link(detail.cell(index + 5, 7), name)
        for rank, rid in enumerate(record["recommended"], 2):
            link(lookup.cell(index + 4, rank), f"BOM_Catalog_{labels[rid]}")
    output = BytesIO()
    workbook.save(output)
    return output.getvalue()
