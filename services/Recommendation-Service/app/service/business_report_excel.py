"""Presentation only for Business AI Report workbooks; no attribution or counts."""
from datetime import date, datetime
from functools import lru_cache
from math import ceil
import unicodedata

from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.page import PageMargins
from openpyxl.worksheet.table import Table, TableStyleInfo

TEA = "245C47"
TEA_LIGHT = "EAF4EE"
WHITE = "FFFFFF"
TEXT = "000000"
MUTED = "69766E"
LINE = "DCE5DF"
DATE_FORMAT = "dd/mm/yyyy"
DATETIME_FORMAT = DATE_FORMAT
INTEGER_FORMAT = "#,##0"
TIMEZONE_LABEL = "Asia/Ho_Chi_Minh (UTC+07:00)"
WIDTHS = ([32, 44, 18, 18, 20], [25, 24, 46, 46, 40],
          [8, 44, 22, 18, 20, 18], [39, 25, 18, 21, 18, 44])
FONT = Font(name="Calibri", size=11, color=TEXT)
SMALL_FONT = Font(name="Calibri", size=10, color=MUTED)
HEADER_FONT = Font(name="Calibri", size=11, bold=True, color=WHITE)
RULE = Side(style="thin", color=LINE)
BODY_BORDER = Border(bottom=RULE)
LEFT = Alignment(horizontal="left", vertical="top", wrap_text=True)
RIGHT = Alignment(horizontal="right", vertical="top", wrap_text=True)
CENTER = Alignment(horizontal="center", vertical="top", wrap_text=True)
# Leave enough height for a repeated table header when printing an A4 page.
MAX_LINES = 20
LINE_HEIGHT = 16


def _cell(ws, row, column, value, font=FONT, fill=None, alignment=LEFT):
    cell = ws.cell(row, column, value)
    cell.font = font
    cell.alignment = alignment
    if isinstance(value, str):
        # This includes text beginning with =, +, -, @, tabs, and formula-like IDs.
        cell.data_type = "s"
        cell.number_format = "@"
    elif isinstance(value, datetime):
        cell.number_format = DATETIME_FORMAT
    elif isinstance(value, date):
        cell.number_format = DATE_FORMAT
    elif isinstance(value, int):
        cell.number_format = INTEGER_FORMAT
        cell.alignment = RIGHT
    if fill:
        cell.fill = fill
    return cell


def _merged(ws, row, first, last, value, **style):
    _cell(ws, row, first, value, **style)
    ws.merge_cells(start_row=row, start_column=first, end_row=row, end_column=last)


def prepare_sheet(ws, from_date, to_date, generated_at, widths):
    columns = len(widths)
    ws.sheet_view.showGridLines = True
    ws.sheet_view.zoomScale = 100
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.sheet_properties.tabColor = None
    for column, width in enumerate(widths, 1):
        ws.column_dimensions[get_column_letter(column)].width = width
    _merged(ws, 1, 1, columns, ws.title,
            font=Font(name="Calibri", size=13, bold=True, color=TEA))
    # Keep real Excel dates. Merges let the period fit above narrow STT columns.
    _merged(ws, 2, 1, 2, from_date)
    _merged(ws, 2, 3, columns, to_date)
    ws.cell(2, 1).number_format = '"Từ ngày: "' + DATE_FORMAT
    ws.cell(2, 3).number_format = '"Đến ngày: "' + DATE_FORMAT
    ws.cell(1, 1).comment = Comment(
        f"Ngày xuất báo cáo (giờ Việt Nam): {generated_at:%d/%m/%Y %H:%M:%S}\n{TIMEZONE_LABEL}",
        "Hương Vân Trà")
    ws.row_dimensions[1].height = 22
    ws.row_dimensions[2].height = 20
    ws.page_setup.orientation = "landscape"
    ws.page_setup.paperSize = ws.PAPERSIZE_A4
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 0
    ws.page_margins = PageMargins(left=0.25, right=0.25, top=0.4, bottom=0.4, header=0.15, footer=0.15)
    ws.print_options.horizontalCentered = True
    ws.oddFooter.left.text = "Hương Vân Trà · &A"
    ws.oddFooter.right.text = "Trang &P / &N"
    for footer in (ws.oddFooter.left, ws.oddFooter.right):
        footer.font = "Calibri"
        footer.size = 9
        footer.color = MUTED


def add_kpis(ws, metrics):
    """Present the existing measures as a two-column table, without cards."""
    ws.page_setup.orientation = "portrait"
    for column, label in enumerate(("Chỉ tiêu", "Giá trị"), 1):
        _cell(ws, 3, column, label, font=HEADER_FONT,
              fill=PatternFill("solid", fgColor=TEA))
    ws.row_dimensions[3].height = 22
    for row, (label, value) in enumerate(metrics, 4):
        for column, content in enumerate((label, value), 1):
            _cell(ws, row, column, content).border = BODY_BORDER
        ws.row_dimensions[row].height = max(20, _visual_lines(label, WIDTHS[0][0]) * LINE_HEIGHT + 4)
    _merged(ws, 8, 1, len(WIDTHS[0]), "THỐNG KÊ SẢN PHẨM",
            font=Font(name="Calibri", size=11, bold=True, color=TEA))
    ws.row_dimensions[8].height = 22


@lru_cache(maxsize=1024)
def _character_width(character):
    if unicodedata.category(character) in {"Mn", "Cf"} or character == "\r":
        return 0
    if character == "\t":
        return 4
    if unicodedata.east_asian_width(character) in {"W", "F"}:
        return 2
    base = unicodedata.normalize("NFD", character)[0]
    if base in "MWmw":
        return 2.2
    return 1.15 if base.isupper() else 1


def _visual_lines(value, width):
    if value is None:
        return 1
    return sum(max(1, ceil(sum(_character_width(c) for c in line) / max(1, int(width - 3))))
               for line in str(value).split("\n"))


def _text_chunks(value, width):
    """Keep every character, even beyond Excel's per-cell text/row-height limits.

    Normal values use one cell. Exceptionally long values continue on subsequent
    physical rows; numeric measures occur only on the first row of the record.
    """
    if not isinstance(value, str) or _visual_lines(value, width) <= MAX_LINES:
        return [value]
    capacity = max(1, int(width - 3))
    fragments, fragment, used_width = [], [], 0
    for character in value:
        size = _character_width(character)
        if used_width + size > capacity and fragment:
            fragments.append("".join(fragment))
            fragment, used_width = [], 0
        fragment.append(character)
        used_width += size
        if character == "\n":
            fragments.append("".join(fragment))
            fragment, used_width = [], 0
    if fragment:
        fragments.append("".join(fragment))
    groups = ["".join(fragments[position:position + MAX_LINES])
              for position in range(0, len(fragments), MAX_LINES)]
    chunks, carry = [], ""
    for index, group in enumerate(groups):
        chunk = carry + group
        carry = ""
        if index < len(groups) - 1:
            boundary = max(chunk.rfind(" "), chunk.rfind("\n")) + 1
            if boundary and len(chunk) - boundary < capacity:
                carry = chunk[boundary:]
                chunk = chunk[:boundary]
        chunks.append(chunk)
    return chunks


def write_table(ws, headers, rows, table_name, widths, totals=None, header=3, repeat_columns=()):
    """Return physical rows for each logical record so evidence comments stay aligned."""
    columns = len(headers)
    for column, value in enumerate(headers, 1):
        _cell(ws, header, column, value, font=HEADER_FONT,
              fill=PatternFill("solid", fgColor=TEA), alignment=Alignment(vertical="center", wrap_text=True))
    ws.row_dimensions[header].height = max(24, max(_visual_lines(v, w) for v, w in zip(headers, widths)) * LINE_HEIGHT + 4)
    record_rows = []
    physical_row = header + 1
    for values in rows:
        chunks = [_text_chunks(value, width) for value, width in zip(values, widths)]
        record = []
        for part in range(max(map(len, chunks))):
            record.append(physical_row)
            displayed = []
            for column, pieces in enumerate(chunks, 1):
                value = (values[column - 1] if part and column - 1 in repeat_columns
                         else pieces[part] if part < len(pieces) else None)
                displayed.append(value)
                cell = _cell(ws, physical_row, column, value)
                cell.border = BODY_BORDER
                if headers[0] == "STT" and column == 1:
                    cell.alignment = CENTER
            ws.row_dimensions[physical_row].height = max(20, max(_visual_lines(v, w) for v, w in zip(displayed, widths)) * LINE_HEIGHT + 4)
            if part:
                ws.cell(physical_row, 1).comment = Comment(
                    f"Dòng tiếp theo của bản ghi ở hàng {record[0]}. Số liệu chỉ ghi ở hàng đầu.", "Hương Vân Trà")
            physical_row += 1
        record_rows.append(record)
    last_data = physical_row - 1
    ref = f"A{header}:{get_column_letter(columns)}{last_data}"
    if rows:
        table = Table(displayName=table_name, ref=ref)
        # Direct cell styles define the brand palette in Excel and LibreOffice.
        table.tableStyleInfo = TableStyleInfo(showRowStripes=False, showColumnStripes=False)
        ws.add_table(table)
    else:
        # A table owns its filter. A second worksheet filter on the same range
        # makes Excel reject the workbook; header-only empty sheets use this one.
        ws.auto_filter.ref = ref
        _merged(ws, physical_row, 1, columns, "Chưa có dữ liệu", font=SMALL_FONT,
                alignment=Alignment(horizontal="center", vertical="center", wrap_text=True))
        ws.row_dimensions[physical_row].height = 22
        physical_row += 1
    if totals is not None:
        for column, value in enumerate(totals, 1):
            cell = _cell(ws, physical_row, column, value,
                         font=Font(name="Calibri", size=11, bold=True, color=TEA),
                         fill=PatternFill("solid", fgColor=TEA_LIGHT))
            cell.border = Border(top=RULE, bottom=RULE)
        ws.row_dimensions[physical_row].height = 22
        physical_row += 1
    if any(len(record) > 1 for record in record_rows):
        _merged(ws, physical_row + 1, 1, columns,
                "Nội dung dài được nối ở các dòng tiếp theo của cùng bản ghi; số liệu chỉ ghi ở dòng đầu.",
                font=SMALL_FONT)
        ws.row_dimensions[physical_row + 1].height = 30
    ws.freeze_panes = "A4" if header == 3 else None
    ws.print_title_rows = f"{header}:{header}"
    return record_rows


def add_methodology(ws, notes):
    row = ws.max_row + 1
    _merged(ws, row, 1, len(WIDTHS[0]), "GHI CHÚ VÀ PHƯƠNG PHÁP THỐNG KÊ",
            font=Font(name="Calibri", size=11, bold=True, color=TEA))
    ws.row_dimensions[row].height = 22
    for note in notes:
        row += 1
        _merged(ws, row, 1, len(WIDTHS[0]), note, font=SMALL_FONT)
        ws.row_dimensions[row].height = max(20, _visual_lines(note, sum(WIDTHS[0]) * 0.85) * 13 + 4)


def finish_print_area(ws):
    ws.print_area = f"A1:{get_column_letter(ws.max_column)}{ws.max_row}"
