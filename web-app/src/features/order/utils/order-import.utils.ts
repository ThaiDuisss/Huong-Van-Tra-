import * as XLSX from "xlsx";

import type { OrderImportRow } from "../types/order-import.types";

/** Phải khớp với OrderImportRequest.MAX_ROWS / MAX_ORDERS ở Order-Service. */
export const ORDER_IMPORT_MAX_ROWS = 1000;
export const ORDER_IMPORT_MAX_ORDERS = 200;
export const ORDER_IMPORT_MAX_FILE_SIZE = 5 * 1024 * 1024;
export const ORDER_IMPORT_TEMPLATE_FILE_NAME = "mau_nhap_don_hang.xlsx";

type ImportField = Exclude<keyof OrderImportRow, "rowNumber">;

const TEMPLATE_COLUMNS: Array<{ field: ImportField; header: string; width: number }> = [
    { field: "orderReference", header: "Mã đơn tham chiếu (*)", width: 22 },
    { field: "soldAt", header: "Ngày bán (dd/MM/yyyy HH:mm)", width: 26 },
    { field: "customerPhone", header: "SĐT khách hàng", width: 18 },
    { field: "customerName", header: "Tên khách hàng", width: 24 },
    { field: "sku", header: "Mã SKU (*)", width: 18 },
    { field: "quantity", header: "Số lượng (*)", width: 12 },
    { field: "unitPrice", header: "Đơn giá (bỏ trống = giá bán hiện tại)", width: 34 },
    { field: "note", header: "Ghi chú", width: 28 }
];

const HEADER_ALIASES: Record<ImportField, string[]> = {
    orderReference: ["madonthamchieu", "madon", "sodon", "mathamchieu", "orderreference", "reference", "ref"],
    soldAt: ["ngayban", "ngaygioban", "thoigianban", "ngay", "soldat", "date"],
    customerPhone: ["sdtkhachhang", "sodienthoaikhachhang", "sodienthoai", "sdt", "dienthoai", "phone", "customerphone"],
    customerName: ["tenkhachhang", "khachhang", "hoten", "customername", "customer"],
    sku: ["masku", "sku", "mahang", "masanpham"],
    quantity: ["soluong", "sl", "quantity", "qty"],
    unitPrice: ["dongia", "giaban", "gia", "unitprice", "price"],
    note: ["ghichu", "note"]
};

const REQUIRED_FIELDS: ImportField[] = ["orderReference", "sku", "quantity"];

export class OrderImportFileError extends Error {}

export function downloadOrderImportTemplate() {
    // Ngày mẫu là hôm qua để nhập thử ngay file mẫu không bị báo "ngày bán ở tương lai".
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const sampleDate = `${pad(yesterday.getDate())}/${pad(yesterday.getMonth() + 1)}/${yesterday.getFullYear()}`;
    const worksheet = XLSX.utils.aoa_to_sheet([
        TEMPLATE_COLUMNS.map((column) => column.header),
        ["DH001", `${sampleDate} 09:30`, "0901234567", "Nguyễn Văn A", "TRA-OLONG-100G", 2, 120000, ""],
        ["DH001", "", "", "", "TRA-SEN-200G", 1, "", "Gói quà"],
        ["DH002", `${sampleDate} 14:15`, "", "Khách lẻ", "TRA-OLONG-100G", 1, "", ""]
    ]);
    worksheet["!cols"] = TEMPLATE_COLUMNS.map((column) => ({ wch: column.width }));

    const guide = XLSX.utils.aoa_to_sheet([
        ["Hướng dẫn nhập đơn hàng"],
        ["- Mỗi dòng là một sản phẩm. Các dòng có cùng \"Mã đơn tham chiếu\" được gộp thành một đơn."],
        ["- Ngày bán, SĐT và tên khách chỉ cần điền ở dòng đầu của mỗi đơn. Bỏ trống ngày bán = thời điểm nhập."],
        ["- SĐT trùng khách hàng đã có sẽ gắn đơn cho khách đó; không trùng thì chỉ lưu tên và SĐT trên đơn."],
        ["- Đơn giá bỏ trống sẽ lấy giá bán hiện tại của sản phẩm."],
        ["- Đơn được ghi nhận là đã thanh toán tiền mặt đủ và trừ tồn kho như đơn bán tại quầy."],
        ["- Mã đơn tham chiếu đã nhập trước đó sẽ bị bỏ qua, nên nhập lại cùng một file không tạo trùng đơn."],
        [`- Tối đa ${ORDER_IMPORT_MAX_ROWS} dòng / ${ORDER_IMPORT_MAX_ORDERS} đơn mỗi lần nhập.`]
    ]);
    guide["!cols"] = [{ wch: 110 }];

    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Đơn hàng");
    XLSX.utils.book_append_sheet(workbook, guide, "Hướng dẫn");
    XLSX.writeFile(workbook, ORDER_IMPORT_TEMPLATE_FILE_NAME);
}

export async function parseOrderImportFile(file: File): Promise<OrderImportRow[]> {
    const extension = file.name.split(".").pop()?.toLowerCase();
    if (extension !== "xlsx" && extension !== "xls" && extension !== "csv") {
        throw new OrderImportFileError(
            "Định dạng file không hợp lệ. Vui lòng chọn file .xlsx, .xls hoặc .csv."
        );
    }
    if (file.size > ORDER_IMPORT_MAX_FILE_SIZE) {
        throw new OrderImportFileError("File vượt quá dung lượng tối đa 5MB.");
    }

    let worksheet: XLSX.WorkSheet | undefined;
    try {
        const workbook = XLSX.read(await file.arrayBuffer(), { type: "array" });
        worksheet = workbook.Sheets[workbook.SheetNames[0]];
    } catch {
        throw new OrderImportFileError(
            "Không đọc được file. Vui lòng kiểm tra lại định dạng file."
        );
    }
    if (!worksheet?.["!ref"]) {
        throw new OrderImportFileError("File không có dữ liệu.");
    }

    const range = XLSX.utils.decode_range(worksheet["!ref"]);
    const columns = new Map<ImportField, number>();
    for (let column = range.s.c; column <= range.e.c; column += 1) {
        const field = fieldFromHeader(cellText(worksheet, range.s.r, column));
        if (field && !columns.has(field)) columns.set(field, column);
    }
    const missing = REQUIRED_FIELDS.filter((field) => !columns.has(field));
    if (missing.length > 0) {
        const labels = missing
            .map((field) => TEMPLATE_COLUMNS.find((column) => column.field === field)?.header)
            .map((header) => `"${header?.replace(/\s*\(.*\)$/, "")}"`)
            .join(", ");
        throw new OrderImportFileError(
            `File thiếu cột bắt buộc ${labels}. Vui lòng dùng file mẫu.`
        );
    }

    const rows: OrderImportRow[] = [];
    for (let rowIndex = range.s.r + 1; rowIndex <= range.e.r; rowIndex += 1) {
        const column = (field: ImportField) => columns.get(field);
        const text = (field: ImportField) => {
            const index = column(field);
            return index === undefined ? "" : cellText(worksheet, rowIndex, index);
        };
        const row: OrderImportRow = {
            rowNumber: rowIndex + 1,
            orderReference: text("orderReference"),
            soldAt: cellDateTime(worksheet, rowIndex, column("soldAt")),
            customerPhone: cellPhone(worksheet, rowIndex, column("customerPhone")),
            customerName: text("customerName"),
            sku: text("sku"),
            quantity: cellNumber(worksheet, rowIndex, column("quantity")),
            unitPrice: cellNumber(worksheet, rowIndex, column("unitPrice")),
            note: text("note")
        };
        const blank = (Object.keys(HEADER_ALIASES) as ImportField[]).every(
            (field) => !row[field]
        );
        if (!blank) rows.push(row);
    }

    if (rows.length === 0) {
        throw new OrderImportFileError("File không có dòng đơn hàng nào.");
    }
    if (rows.length > ORDER_IMPORT_MAX_ROWS) {
        throw new OrderImportFileError(
            `File có ${rows.length.toLocaleString("vi-VN")} dòng, vượt giới hạn ${ORDER_IMPORT_MAX_ROWS.toLocaleString("vi-VN")} dòng mỗi lần nhập. Vui lòng tách file.`
        );
    }
    return rows;
}

function fieldFromHeader(header: string): ImportField | null {
    const key = header
        .replace(/\(.*?\)/g, "")
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "")
        .replace(/đ/gi, "d")
        .toLowerCase()
        .replace(/[^a-z0-9]/g, "");
    if (!key) return null;
    const fields = Object.keys(HEADER_ALIASES) as ImportField[];
    return fields.find((field) => HEADER_ALIASES[field].includes(key)) ?? null;
}

function cellAt(worksheet: XLSX.WorkSheet, row: number, column: number) {
    return worksheet[XLSX.utils.encode_cell({ r: row, c: column })] as
        | XLSX.CellObject
        | undefined;
}

function cellText(worksheet: XLSX.WorkSheet, row: number, column: number) {
    const cell = cellAt(worksheet, row, column);
    if (!cell || cell.v === undefined || cell.v === null) return "";
    return String(cell.w ?? cell.v).trim();
}

/** Ô số gửi giá trị gốc (không định dạng hàng nghìn) để server không hiểu nhầm dấu phân cách. */
function cellNumber(worksheet: XLSX.WorkSheet, row: number, column: number | undefined) {
    if (column === undefined) return "";
    const cell = cellAt(worksheet, row, column);
    if (cell?.t === "n" && typeof cell.v === "number") return String(cell.v);
    return cellText(worksheet, row, column);
}

/** Excel bỏ số 0 đầu của SĐT gõ vào ô số (0901234567 -> 901234567): bù lại khi đủ 9 chữ số. */
function cellPhone(worksheet: XLSX.WorkSheet, row: number, column: number | undefined) {
    if (column === undefined) return "";
    const text = cellText(worksheet, row, column);
    const cell = cellAt(worksheet, row, column);
    return cell?.t === "n" && /^[1-9]\d{8}$/.test(text) ? `0${text}` : text;
}

/** Ô ngày kiểu số của Excel đổi sang yyyy-MM-ddTHH:mm; ô dạng chữ giữ nguyên để server kiểm tra. */
function cellDateTime(worksheet: XLSX.WorkSheet, row: number, column: number | undefined) {
    if (column === undefined) return "";
    const cell = cellAt(worksheet, row, column);
    if (!cell || cell.v === undefined || cell.v === null) return "";
    if (cell.t === "n" && typeof cell.v === "number") {
        const parsed = XLSX.SSF.parse_date_code(cell.v);
        if (parsed) {
            return `${pad(parsed.y, 4)}-${pad(parsed.m)}-${pad(parsed.d)}T${pad(parsed.H)}:${pad(parsed.M)}`;
        }
    }
    if (cell.v instanceof Date) {
        const value = cell.v;
        return `${pad(value.getFullYear(), 4)}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
    }
    return String(cell.w ?? cell.v).trim();
}

function pad(value: number, length = 2) {
    return String(value).padStart(length, "0");
}
