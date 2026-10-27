import * as XLSX from "xlsx";

import type { CustomerImportRow } from "../types/customer.types";

/** Phải khớp với CustomerImportRequest.MAX_ROWS ở Customer-Service. */
export const CUSTOMER_IMPORT_MAX_ROWS = 500;
export const CUSTOMER_IMPORT_MAX_FILE_SIZE = 5 * 1024 * 1024;
export const CUSTOMER_IMPORT_TEMPLATE_FILE_NAME = "mau_nhap_khach_hang.xlsx";

type ImportField = Exclude<keyof CustomerImportRow, "rowNumber">;

const TEMPLATE_COLUMNS: Array<{ field: ImportField; header: string; width: number }> = [
    { field: "fullName", header: "Tên khách hàng (*)", width: 28 },
    { field: "phone", header: "Số điện thoại (*)", width: 18 },
    { field: "email", header: "Email", width: 28 },
    { field: "dateOfBirth", header: "Ngày sinh (dd/MM/yyyy)", width: 22 },
    { field: "customerType", header: "Loại khách hàng (Cá nhân / Doanh nghiệp)", width: 38 },
    { field: "customerCode", header: "Mã khách hàng (bỏ trống để tự sinh)", width: 34 }
];

const HEADER_ALIASES: Record<ImportField, string[]> = {
    fullName: ["tenkhachhang", "hovaten", "hoten", "ten", "fullname", "name"],
    phone: ["sodienthoai", "dienthoai", "sdt", "phone"],
    email: ["email"],
    dateOfBirth: ["ngaysinh", "dateofbirth", "dob"],
    customerType: ["loaikhachhang", "loai", "customertype"],
    customerCode: ["makhachhang", "makh", "ma", "customercode"]
};

export class CustomerImportFileError extends Error {}

export function downloadCustomerImportTemplate() {
    const worksheet = XLSX.utils.aoa_to_sheet([
        TEMPLATE_COLUMNS.map((column) => column.header),
        ["Nguyễn Văn A", "0901234567", "nguyenvana@example.com", "15/08/1990", "Cá nhân", ""],
        ["Công ty TNHH Trà Việt", "0287654321", "", "", "Doanh nghiệp", ""]
    ]);
    worksheet["!cols"] = TEMPLATE_COLUMNS.map((column) => ({ wch: column.width }));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Khách hàng");
    XLSX.writeFile(workbook, CUSTOMER_IMPORT_TEMPLATE_FILE_NAME);
}

export async function parseCustomerImportFile(file: File): Promise<CustomerImportRow[]> {
    const extension = file.name.split(".").pop()?.toLowerCase();
    if (extension !== "xlsx" && extension !== "xls") {
        throw new CustomerImportFileError(
            "Định dạng file không hợp lệ. Vui lòng chọn file .xlsx hoặc .xls."
        );
    }
    if (file.size > CUSTOMER_IMPORT_MAX_FILE_SIZE) {
        throw new CustomerImportFileError("File vượt quá dung lượng tối đa 5MB.");
    }

    let worksheet: XLSX.WorkSheet | undefined;
    try {
        const workbook = XLSX.read(await file.arrayBuffer(), { type: "array" });
        worksheet = workbook.Sheets[workbook.SheetNames[0]];
    } catch {
        throw new CustomerImportFileError(
            "Không đọc được file Excel. Vui lòng kiểm tra lại định dạng file."
        );
    }
    if (!worksheet?.["!ref"]) {
        throw new CustomerImportFileError("File không có dữ liệu.");
    }

    const range = XLSX.utils.decode_range(worksheet["!ref"]);
    const columns = new Map<ImportField, number>();
    for (let column = range.s.c; column <= range.e.c; column += 1) {
        const field = fieldFromHeader(cellText(worksheet, range.s.r, column));
        if (field && !columns.has(field)) columns.set(field, column);
    }
    if (!columns.has("fullName") || !columns.has("phone")) {
        throw new CustomerImportFileError(
            "File thiếu cột bắt buộc \"Tên khách hàng\" hoặc \"Số điện thoại\". Vui lòng dùng file mẫu."
        );
    }

    const rows: CustomerImportRow[] = [];
    for (let rowIndex = range.s.r + 1; rowIndex <= range.e.r; rowIndex += 1) {
        const read = (field: ImportField) => {
            const column = columns.get(field);
            return column === undefined ? "" : cellText(worksheet, rowIndex, column);
        };
        const dateColumn = columns.get("dateOfBirth");
        const row: CustomerImportRow = {
            rowNumber: rowIndex + 1,
            fullName: read("fullName"),
            phone: cellPhone(worksheet, rowIndex, columns.get("phone")),
            email: read("email"),
            dateOfBirth:
                dateColumn === undefined ? "" : cellDate(worksheet, rowIndex, dateColumn),
            customerType: read("customerType"),
            customerCode: read("customerCode")
        };
        const blank = (Object.keys(HEADER_ALIASES) as ImportField[]).every(
            (field) => !row[field]
        );
        if (!blank) rows.push(row);
    }

    if (rows.length === 0) {
        throw new CustomerImportFileError("File không có dòng khách hàng nào.");
    }
    if (rows.length > CUSTOMER_IMPORT_MAX_ROWS) {
        throw new CustomerImportFileError(
            `File có ${rows.length.toLocaleString("vi-VN")} dòng, vượt giới hạn ${CUSTOMER_IMPORT_MAX_ROWS} dòng mỗi lần nhập. Vui lòng tách file.`
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

/** Ưu tiên chuỗi hiển thị của Excel để giữ số 0 đầu của số điện thoại khi ô có định dạng. */
function cellText(worksheet: XLSX.WorkSheet, row: number, column: number) {
    const cell = cellAt(worksheet, row, column);
    if (!cell || cell.v === undefined || cell.v === null) return "";
    return String(cell.w ?? cell.v).trim();
}

/**
 * Excel lưu số điện thoại gõ tay thành ô số và bỏ mất số 0 đầu (0901234567 -> 901234567).
 * Với ô kiểu số có đúng 9 chữ số, bù lại số 0 đầu; giá trị sau khi bù hiển thị ở bảng xem trước.
 */
function cellPhone(worksheet: XLSX.WorkSheet, row: number, column: number | undefined) {
    if (column === undefined) return "";
    const text = cellText(worksheet, row, column);
    const cell = cellAt(worksheet, row, column);
    return cell?.t === "n" && /^[1-9]\d{8}$/.test(text) ? `0${text}` : text;
}

/** Ô ngày kiểu số của Excel được đổi sang yyyy-MM-dd; ô dạng chữ giữ nguyên để server kiểm tra. */
function cellDate(worksheet: XLSX.WorkSheet, row: number, column: number) {
    const cell = cellAt(worksheet, row, column);
    if (!cell || cell.v === undefined || cell.v === null) return "";
    if (cell.t === "n" && typeof cell.v === "number") {
        const parsed = XLSX.SSF.parse_date_code(cell.v);
        if (parsed) {
            return `${String(parsed.y).padStart(4, "0")}-${String(parsed.m).padStart(2, "0")}-${String(parsed.d).padStart(2, "0")}`;
        }
    }
    if (cell.v instanceof Date) {
        const value = cell.v;
        return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
    }
    return String(cell.w ?? cell.v).trim();
}
