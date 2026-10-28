"""Sale/POS presentation of frozen historical evaluation; no inference or source mutation."""
from collections import defaultdict
from datetime import timezone, timedelta
from io import BytesIO
from math import isclose

from openpyxl import Workbook, load_workbook
from openpyxl.comments import Comment
from openpyxl.utils.cell import range_boundaries

from app.service.business_report_excel import (
    WIDTHS as OPERATIONAL_WIDTHS, RIGHT, LINE_HEIGHT, _visual_lines, add_kpis, add_methodology, finish_print_area, prepare_sheet, write_table,
)

SHEETS = ["TỔNG QUAN BÁO CÁO", "CHI TIẾT THEO KHÁCH HÀNG", "THỐNG KÊ SẢN PHẨM", "ĐỐI CHIẾU KẾT QUẢ GỢI Ý"]
HEADERS = [
    ["STT", "Sản phẩm", "Lượt AI gợi ý", "Số lượng sản phẩm bán", "Lượt gợi ý trùng mua"],
    ["Mã KH", "Ngày gợi ý", "AI đề xuất", "Sản phẩm khách mua", "Sản phẩm trùng", "Tỷ lệ gợi ý trùng mua (%)"],
    ["STT", "Sản phẩm", "Số khách được gợi ý", "Lượt gợi ý", "Số đơn có mua", "Lượt gợi ý trùng mua", "Tỷ lệ trùng mua (%)", "Số lượng sản phẩm bán"],
    ["Mã gợi ý", "Mã KH", "Số món gợi ý", "Số món khách mua", "Số món trùng", "Tỷ lệ trùng mua (%)"],
]
WIDTHS = (OPERATIONAL_WIDTHS[0], [13, 18, 42, 42, 38, 24],
          [7, 38, 17, 16, 18, 18, 19, 20], [14, 13, 18, 21, 18, 24])
MARKER = "DỮ LIỆU KIỂM THỬ"
PRESENTATION_SCHEMA = "sale-pos-ai-report-v2"
PERCENT_FORMAT = "0.00%"
UNKNOWN = "—"
SOURCE_NOTE = ("Báo cáo sử dụng lịch sử mua hàng để kiểm tra mức độ phù hợp của sản phẩm AI đề xuất. "
               "Tỷ lệ trùng mua không phải tỷ lệ doanh số phát sinh do AI.")
CALCULATION_NOTE = "Tỷ lệ trùng mua = Số sản phẩm gợi ý trùng với sản phẩm khách mua / Tổng số sản phẩm được gợi ý."
LIMITATION_NOTE = ("Dữ liệu kiểm thử; chưa có bằng chứng khách đã xem đề xuất. "
                   "— ở số lượng bán: chưa xác minh đơn vị; — ở ngày gợi ý: chưa có thời điểm thực.")
STATUSES = {"OFFLINE_MATCHED": "Có sản phẩm trùng", "OFFLINE_NO_OVERLAP": "Không có sản phẩm trùng",
            "NO_CANDIDATES": "Không có gợi ý", "EMPTY_GROUND_TRUTH": "Chưa có dữ liệu đối chiếu",
            "INPUT_NOT_VERIFIED": "Chưa đủ dữ liệu xác minh"}


def match_rate(matched, recommended):
    """Binary opportunity-product pairs; no order or quantity weighting."""
    if not 0 <= matched <= recommended:
        raise ValueError("Matched count must be between zero and recommended count")
    return matched / recommended if recommended else None


def opportunity_match_value(row):
    """One display value per stable opportunity, shared by both customer-facing tables."""
    if not row.prediction.items:
        return "Không có gợi ý"
    if not row.ground_truth:
        return "Chưa có dữ liệu đối chiếu"
    return match_rate(len(row.hits), len(row.prediction.items))


def display_identifiers(result):
    """Display-only aliases in frozen prediction order; protected references stay internal."""
    return tuple(dict(customer_display=f"KH-{n:03d}", opportunity_display=f"GY-{n:03d}",
                      customer_reference=p.customer, opportunity_reference=p.opportunity_ref,
                      simulated_cutoff=p.cutoff, state_sha256=p.state_sha256)
                 for n, p in enumerate(result.frozen.predictions, 1))


def _percentage(cell):
    if isinstance(cell.value, (int, float)):
        cell.number_format = PERCENT_FORMAT
        cell.alignment = RIGHT
        # Fit the displayed percentage, not the raw float decimal expansion.
        ws = cell.parent
        widths = WIDTHS[SHEETS.index(ws.title)]
        values = [f"{c.value:.2%}" if c.number_format == PERCENT_FORMAT and isinstance(c.value, (int, float))
                  else c.value for c in ws[cell.row]]
        ws.row_dimensions[cell.row].height = max(20, max(_visual_lines(v, w) for v, w in zip(values, widths)) * LINE_HEIGHT + 4)


def product_statistics(result):
    stats = defaultdict(lambda: dict(customers=set(), appearances=0, orders=set(), matches=0))
    for row in result.rows:
        for item in row.prediction.items:
            values = stats[item.product_id]
            values["customers"].add(row.prediction.customer)
            values["appearances"] += 1
        for product in row.hits:
            stats[product]["matches"] += 1
        for order_id, product_ids in row.test_order_products:
            for product in product_ids:
                stats[product]["orders"].add(order_id)
    return stats


def create_workbook(result, products, split_manifest, executed_at, period, source="ARCHIVE", marker=MARKER, period_notes=()):
    result.frozen.verify()
    if executed_at.tzinfo is None:
        raise ValueError("Workbook execution clock must be timezone-aware")
    metrics = result.metrics
    stats = product_statistics(result)
    views = {p.product_id: p for p in products}
    name = lambda p: views[p].name if p in views else "Sản phẩm ngoài danh mục hiện tại"
    ordered_products = sorted(stats, key=lambda p: (name(p), p))
    aliases = {a["opportunity_reference"]: a for a in display_identifiers(result)}
    rates = {r.prediction.opportunity_ref: opportunity_match_value(r) for r in result.rows}
    wb = Workbook()
    wb.remove(wb.active)
    wb.properties.creator = "Hương Vân Trà"
    wb.properties.title = "BÁO CÁO HIỆU QUẢ GỢI Ý SẢN PHẨM"
    wb.properties.subject = "Dữ liệu kiểm thử từ lịch sử mua hàng"
    wb.properties.description = SOURCE_NOTE
    local_generated = executed_at.astimezone(timezone(timedelta(hours=7))).replace(tzinfo=None)
    for title, widths in zip(SHEETS, WIDTHS):
        ws = wb.create_sheet(title)
        prepare_sheet(ws, period[0], period[1], local_generated, widths)
        ws["A1"] = title + " · " + marker
        ws["A1"].comment = Comment(ws["A1"].comment.text + "\nDữ liệu kiểm thử từ lịch sử mua hàng.\n" + SOURCE_NOTE,
                                   "Hương Vân Trà")
    overview = wb.worksheets[0]
    overall = match_rate(metrics["total_matched_opportunity_product_pairs"], metrics["total_recommendation_items"])
    add_kpis(overview, [
        ("Khách hàng được gợi ý", metrics["customers_with_nonempty_recommendation"]),
        ("Lượt gợi ý sản phẩm", metrics["total_recommendation_items"]),
        ("Lượt gợi ý trùng mua", metrics["total_matched_opportunity_product_pairs"]),
        ("Tỷ lệ gợi ý trùng mua (%)", overall if overall is not None else UNKNOWN),
    ])
    _percentage(overview["B7"])
    overview["B7"].comment = Comment("Lượt gợi ý trùng mua chia lượt gợi ý sản phẩm. Không phải tỷ lệ khách mua do AI thuyết phục.", "Hương Vân Trà")
    product_rows = [[n, name(p), stats[p]["appearances"], UNKNOWN, stats[p]["matches"]]
                    for n, p in enumerate(ordered_products, 1)]
    write_table(overview, HEADERS[0], product_rows, "Overview", WIDTHS[0], header=9,
                totals=[None, "Tổng", metrics["total_recommendation_items"], UNKNOWN,
                        metrics["total_matched_opportunity_product_pairs"]])
    add_methodology(overview, [SOURCE_NOTE, CALCULATION_NOTE, LIMITATION_NOTE])
    details, comparisons = [], []
    for row in result.rows:
        p = row.prediction
        alias = aliases[p.opportunity_ref]
        status = STATUSES[row.status]
        suggested = "\n".join(name(i.product_id) for i in p.items) or "Không có gợi ý"
        truth = "\n".join(name(i) for i in row.ground_truth) or "Chưa có dữ liệu đối chiếu"
        matches = "\n".join(name(i) for i in row.hits) or status
        rate = rates[p.opportunity_ref]
        details.append([alias["customer_display"], UNKNOWN, suggested, truth, matches, rate])
        comparisons.append([alias["opportunity_display"], alias["customer_display"], len(p.items),
                            len(row.ground_truth), len(row.hits) if row.ground_truth else None, rate])
    physical = write_table(wb.worksheets[1], HEADERS[1], details, "Customers", WIDTHS[1], repeat_columns=(0, 1))
    for row, positions in zip(result.rows, physical):
        _percentage(wb.worksheets[1].cell(positions[0], 6))
        alias = aliases[row.prediction.opportunity_ref]
        for position in positions:
            wb.worksheets[1].cell(position, 1).comment = Comment("Mã gợi ý: " + alias["opportunity_display"], "Hương Vân Trà")
            wb.worksheets[1].cell(position, 2).comment = Comment("Không có ngày gợi ý thực tế. Báo cáo dùng dữ liệu mua hàng lịch sử để đối chiếu.", "Hương Vân Trà")
    rows = []
    for n, p in enumerate(ordered_products, 1):
        values = stats[p]
        rate = match_rate(values["matches"], values["appearances"])
        rows.append([n, name(p), len(values["customers"]), values["appearances"], len(values["orders"]),
                     values["matches"], rate if rate is not None else "Chưa được gợi ý", UNKNOWN])
    physical = write_table(wb.worksheets[2], HEADERS[2], rows, "Products", WIDTHS[2])
    for positions in physical:
        _percentage(wb.worksheets[2].cell(positions[0], 7))
    physical = write_table(wb.worksheets[3], HEADERS[3], comparisons, "Matches", WIDTHS[3], repeat_columns=(0, 1))
    for row, positions in zip(result.rows, physical):
        missed = [name(p) for p in row.ground_truth if p not in row.hits]
        for position in positions:
            cell = wb.worksheets[3].cell(position, 6)
            _percentage(cell)
            cell.comment = Comment("Kết quả đối chiếu: " + STATUSES[row.status] + ".\nTỷ lệ = số món trùng / số món gợi ý.\n"
                                   + ("Chưa có dữ liệu mua hàng đối chiếu." if not row.ground_truth else
                                      "Sản phẩm khách mua chưa được gợi ý: " + "; ".join(missed) if missed else "Không còn sản phẩm mua ngoài danh sách gợi ý."), "Hương Vân Trà")
    for ws in wb:
        finish_print_area(ws)
    return wb


def export_workbook(*args, **kwargs):
    output = BytesIO()
    create_workbook(*args, **kwargs).save(output)
    return output.getvalue()


def _table_rows(ws, name):
    if name not in ws.tables:
        return []
    a, b, c, d = range_boundaries(ws.tables[name].ref)
    return list(ws.iter_rows(min_row=b + 1, max_row=d, min_col=a, max_col=c, values_only=True))


def verify_workbook(content, result, marker=MARKER):
    """Reconcile serialized business-facing percentages and counts against frozen results."""
    wb = load_workbook(BytesIO(content))
    if wb.sheetnames != SHEETS:
        raise ValueError("Workbook sheet contract changed")
    for index, ws in enumerate(wb):
        header = 9 if index == 0 else 3
        if ([c.value for c in ws[header]] != HEADERS[index]
                or ws.freeze_panes != (None if index == 0 else "A4")
                or not ws.sheet_view.showGridLines or marker not in ws["A1"].value):
            raise ValueError("Workbook presentation contract changed")
    m = result.metrics
    if [wb.worksheets[0][c].value for c in ("B4", "B5", "B6")] != [
            m["customers_with_nonempty_recommendation"], m["total_recommendation_items"],
            m["total_matched_opportunity_product_pairs"]]:
        raise ValueError("Workbook KPIs differ from evaluation")
    def percentage(cell, expected, missing):
        if expected is None:
            valid = cell.value == missing
        else:
            valid = (isinstance(cell.value, (int, float)) and not isinstance(cell.value, bool)
                     and 0 <= cell.value <= 1 and isclose(cell.value, expected, abs_tol=1e-12)
                     and cell.number_format == PERCENT_FORMAT)
        if not valid:
            raise ValueError("Workbook percentage differs from matched/recommended")
    percentage(wb.worksheets[0]["B7"], match_rate(m["total_matched_opportunity_product_pairs"], m["total_recommendation_items"]), UNKNOWN)
    aliases = display_identifiers(result)
    by_ref = {r.prediction.opportunity_ref: r for r in result.rows}
    alias_by_ref = {a["opportunity_reference"]: a for a in aliases}
    ref_by_suggestion = {a["opportunity_display"]: a["opportunity_reference"] for a in aliases}
    ref_by_customer = {a["customer_display"]: a["opportunity_reference"] for a in aliases}
    compared = _table_rows(wb.worksheets[3], "Matches")
    if len(compared) != len(by_ref) or len(by_ref) != len(result.rows) or set(by_ref) != set(alias_by_ref):
        raise ValueError("Workbook opportunity counts differ")
    comparison_cells = {}
    for physical_row, saved in enumerate(compared, 4):
        ref = ref_by_suggestion.get(saved[0])
        if ref not in by_ref or ref in comparison_cells:
            raise ValueError("Workbook opportunity identity differs")
        row, alias = by_ref[ref], alias_by_ref[ref]
        p = row.prediction
        if list(saved[:5]) != [alias["opportunity_display"], alias["customer_display"], len(p.items),
                               len(row.ground_truth), len(row.hits) if row.ground_truth else None]:
            raise ValueError("Workbook opportunity identity or matching differs")
        cell = wb.worksheets[3].cell(physical_row, 6)
        expected = opportunity_match_value(row)
        percentage(cell, expected if isinstance(expected, (int, float)) else None, expected)
        comparison_cells[ref] = cell
    stats = product_statistics(result)
    products = [r for r in _table_rows(wb.worksheets[2], "Products") if r[0] is not None]
    if (sum(r[3] for r in products) != m["total_recommendation_items"]
            or sum(r[5] for r in products) != m["total_matched_opportunity_product_pairs"]
            or sum(r[4] for r in products) != sum(len(v["orders"]) for v in stats.values())
            or any(r[7] != UNKNOWN for r in products)):
        raise ValueError("Workbook product statistics differ")
    customer_rows = _table_rows(wb.worksheets[1], "Customers")
    customer_cells = defaultdict(list)
    for physical_row, saved in enumerate(customer_rows, 4):
        ref = ref_by_customer.get(saved[0])
        if ref not in by_ref or saved[1] != UNKNOWN:
            raise ValueError("Workbook display identifiers or recommendation dates differ")
        customer_cells[ref].append(wb.worksheets[1].cell(physical_row, 6))
    if set(customer_cells) != set(by_ref):
        raise ValueError("Workbook customer cohort differs")
    for ref, cells in customer_cells.items():
        # Continuations may move under sorting; associate by protected alias identity, not row position.
        primary = [c for c in cells if c.value is not None]
        if len(primary) != 1:
            raise ValueError("Workbook customer percentage must occur once per opportunity")
        expected = opportunity_match_value(by_ref[ref])
        percentage(primary[0], expected if isinstance(expected, (int, float)) else None, expected)
        if primary[0].value != comparison_cells[ref].value:
            raise ValueError("Customer and comparison percentages differ for the same opportunity")
    for ws in wb:
        if any(c.data_type == "f" for row in ws for c in row):
            raise ValueError("Workbook must contain literal values, not injected formulas")
    # Product rates are checked on each first physical row; continuation rows carry no counts.
    for cells in wb.worksheets[2].iter_rows(min_row=4):
        if isinstance(cells[0].value, int):
            percentage(cells[6], match_rate(cells[5].value, cells[3].value), "Chưa được gợi ý")
    return {"sheet_names": wb.sheetnames, "opportunities": len(result.rows),
            "kpi_reconciliation": "PASS", "product_statistics_reconciliation": "PASS",
            "percentage_reconciliation": "PASS", "customer_percentage_reconciliation": "PASS",
            "customer_percentages_checked": len(by_ref), "display_identifiers": "PASS",
            "offline_identification": "PASS", "quantity": "NOT_VERIFIED; displayed —", "formula_injection": "PASS"}
