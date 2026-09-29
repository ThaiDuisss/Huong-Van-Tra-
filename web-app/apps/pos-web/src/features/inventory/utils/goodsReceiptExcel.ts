import * as XLSX from "xlsx-js-style";
import { goodsReceiptApi } from "../services/goodsReceipt.api";
import type {
    GoodsReceiptOptions,
    GoodsReceiptStatus,
    GoodsReceiptExportRow,
    QcResult
} from "../types/goods-receipt.types";

export function formatGoodsReceiptStatus(status: GoodsReceiptStatus): string {
    switch (status) {
        case "DRAFT":
            return "Bản nháp";
        case "PENDING_APPROVAL":
        case "SUBMITTED":
            return "Chờ duyệt";
        case "APPROVED":
            return "Đã duyệt";
        case "RECEIVING":
            return "Đang tiếp nhận";
        case "QC_PENDING":
        case "AWAITING_QC":
            return "Chờ QC";
        case "QC_COMPLETED":
        case "READY_TO_POST":
            return "Chờ ghi sổ";
        case "POSTED":
            return "Đã nhập kho";
        case "REJECTED":
            return "Từ chối";
        case "CANCELLED":
            return "Đã hủy";
        case "PARTIALLY_PASSED":
            return "QC đạt một phần";
        case "AWAITING_COST":
            return "Chờ tính giá";
        default:
            return status;
    }
}

export function formatQcResult(qcResult?: QcResult): string {
    switch (qcResult) {
        case "PASSED":
            return "Đạt chuẩn";
        case "PARTIALLY_PASSED":
            return "Đạt một phần";
        case "FAILED":
            return "Không đạt";
        case "PENDING":
        default:
            return "Chờ kiểm định";
    }
}

export function exportGoodsReceiptsToExcel(receipts: GoodsReceiptExportRow[]) {
    const now = new Date();
    const dateFormatted = now.toLocaleString("vi-VN");

    const headerRows = [
        ["", "CỬA HÀNG HƯƠNG VÂN TRÀ"],
        ["", "BÁO CÁO PHIẾU NHẬP KHO TỪ NHÀ CUNG CẤP"],
        ["", `Thời gian xuất: ${dateFormatted}`],
        [], // Dòng trống cách biệt
        [
            "",
            "STT",
            "Mã phiếu nhập",
            "Mã đơn mua (PO)",
            "Nhà cung cấp",
            "Kho nhận",
            "Trạng thái",
            "Kết quả QC",
            "Số dòng mặt hàng",
            "Tổng SL nhận",
            "Ngày nhận",
            "Người tạo"
        ]
    ];

    const dataRows = receipts.map((r, index) => {
        const dateStr = r.receivedAt ? new Date(r.receivedAt).toLocaleString("vi-VN") : "";
        return [
            "",
            index + 1,
            r.code || "",
            r.purchaseOrderCode || "",
            r.supplierName || "",
            r.receivingLocationName || "",
            formatGoodsReceiptStatus(r.status),
            formatQcResult(r.qcResult),
            r.totalLines || 0,
            r.totalReceivedQuantity || 0,
            dateStr,
            r.createdByName || ""
        ];
    });

    const worksheet = XLSX.utils.aoa_to_sheet([...headerRows, ...dataRows]);

    // Styles
    const brandStyle = {
        font: { bold: true, sz: 13, color: { rgb: "07883A" } },
        alignment: { vertical: "center" }
    };
    const titleStyle = {
        font: { bold: true, sz: 16, color: { rgb: "111827" } },
        alignment: { vertical: "center" }
    };
    const subTitleStyle = {
        font: { italic: true, sz: 10, color: { rgb: "6B7280" } },
        alignment: { vertical: "center" }
    };
    const headerStyle = {
        fill: {
            patternType: "solid",
            fgColor: { rgb: "07883A" }
        },
        font: {
            bold: true,
            sz: 11,
            color: { rgb: "FFFFFF" }
        },
        alignment: {
            horizontal: "center",
            vertical: "center",
            wrapText: true
        },
        border: {
            top: { style: "thin", color: { rgb: "D1D5DB" } },
            bottom: { style: "thin", color: { rgb: "D1D5DB" } },
            left: { style: "thin", color: { rgb: "D1D5DB" } },
            right: { style: "thin", color: { rgb: "D1D5DB" } }
        }
    };
    const dataCellStyle = {
        font: { sz: 11 },
        alignment: { vertical: "center" },
        border: {
            top: { style: "thin", color: { rgb: "E5E7EB" } },
            bottom: { style: "thin", color: { rgb: "E5E7EB" } },
            left: { style: "thin", color: { rgb: "E5E7EB" } },
            right: { style: "thin", color: { rgb: "E5E7EB" } }
        }
    };
    const centerDataCellStyle = {
        font: { sz: 11 },
        alignment: { horizontal: "center", vertical: "center" },
        border: {
            top: { style: "thin", color: { rgb: "E5E7EB" } },
            bottom: { style: "thin", color: { rgb: "E5E7EB" } },
            left: { style: "thin", color: { rgb: "E5E7EB" } },
            right: { style: "thin", color: { rgb: "E5E7EB" } }
        }
    };

    // Apply styles to cells
    if (worksheet["B1"]) worksheet["B1"].s = brandStyle;
    if (worksheet["B2"]) worksheet["B2"].s = titleStyle;
    if (worksheet["B3"]) worksheet["B3"].s = subTitleStyle;

    const totalCols = headerRows[4].length;
    const totalRows = headerRows.length + dataRows.length;

    // Header row is row 5 (0-indexed row 4), starting at col 1 (Column B)
    for (let c = 1; c < totalCols; c++) {
        const cellRef = XLSX.utils.encode_cell({ r: 4, c });
        if (worksheet[cellRef]) {
            worksheet[cellRef].s = headerStyle;
        }
    }

    // Data rows start at row 6 (0-indexed row 5), starting at col 1 (Column B)
    for (let r = 5; r < totalRows; r++) {
        for (let c = 1; c < totalCols; c++) {
            const cellRef = XLSX.utils.encode_cell({ r, c });
            if (worksheet[cellRef]) {
                const isCenter = c === 1 || c === 6 || c === 7 || c === 8 || c === 9 || c === 10;
                worksheet[cellRef].s = isCenter ? centerDataCellStyle : dataCellStyle;
            }
        }
    }

    // Set column widths
    const colWidths = [
        { wch: 4 },  // Cột A (Trống làm lề)
        { wch: 8 },  // STT
        { wch: 20 }, // Mã phiếu
        { wch: 20 }, // Mã đơn mua
        { wch: 32 }, // Nhà cung cấp
        { wch: 26 }, // Kho nhận
        { wch: 18 }, // Trạng thái
        { wch: 18 }, // Kết quả QC
        { wch: 20 }, // Số dòng mặt hàng
        { wch: 16 }, // Tổng SL nhận
        { wch: 22 }, // Ngày nhận
        { wch: 22 }  // Người tạo
    ];
    worksheet["!cols"] = colWidths;

    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "Danh sách Phiếu nhập");

    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, "0");
    const day = String(now.getDate()).padStart(2, "0");
    const hours = String(now.getHours()).padStart(2, "0");
    const minutes = String(now.getMinutes()).padStart(2, "0");
    const filename = `Danh_sach_phieu_nhap_kho_${year}${month}${day}_${hours}${minutes}.xlsx`;

    XLSX.writeFile(workbook, filename);
}

export function getExcelRowValue(row: any, ...keys: string[]): string {
    if (!row) return "";
    for (const k of keys) {
        if (row[k] !== undefined && row[k] !== null) {
            return String(row[k]).trim();
        }
    }
    const rowKeys = Object.keys(row);
    for (const targetKey of keys) {
        const normalizedTarget = targetKey.toLowerCase().replace(/[^a-z0-9]/g, "");
        const foundKey = rowKeys.find((rk) => {
            const normalizedRk = rk.toLowerCase().replace(/[^a-z0-9]/g, "");
            return normalizedRk === normalizedTarget;
        });
        if (foundKey && row[foundKey] !== undefined && row[foundKey] !== null) {
            return String(row[foundKey]).trim();
        }
    }
    return "";
}

export async function parseAndImportGoodsReceipts(file: File, options: GoodsReceiptOptions): Promise<void> {
    const data = await file.arrayBuffer();
    const workbook = XLSX.read(new Uint8Array(data), { type: "array" });
    const sheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[sheetName];
    const rawRows = XLSX.utils.sheet_to_json<any>(worksheet);

    if (!rawRows || rawRows.length === 0) {
        throw new Error("File Excel không có dữ liệu!");
    }

    interface ParsedItem {
        supplierId: string;
        locationId: string;
        purchaseOrderCode: string;
        materialId: string;
        orderedQuantity: number;
        supplierLot: string;
        note: string;
    }

    const itemsToCreate: ParsedItem[] = [];

    for (let i = 0; i < rawRows.length; i++) {
        const row = rawRows[i];
        const rowNum = i + 2;

        const supplierCodeOrName = getExcelRowValue(row, "Mã nhà cung cấp", "Tên nhà cung cấp", "Nhà cung cấp", "Supplier", "Supplier Code");
        const locationCodeOrName = getExcelRowValue(row, "Mã kho nhận", "Tên kho nhận", "Kho nhận", "Kho", "Location", "Location Code");
        const poCode = getExcelRowValue(row, "Mã đơn mua (PO)", "Mã đơn mua", "Mã PO", "PO", "Purchase Order", "PO Code");
        const materialCodeOrName = getExcelRowValue(row, "Mã nguyên liệu", "Tên nguyên liệu", "Nguyên liệu", "Mã SKU", "SKU", "Material");
        const quantityStr = getExcelRowValue(row, "Số lượng đặt", "Số lượng", "SL đặt", "Quantity", "Ordered Quantity");
        const supplierLot = getExcelRowValue(row, "Số lô NCC (Tùy chọn)", "Số lô NCC", "Lô NCC", "Số lô", "Lot", "Supplier Lot");
        const note = getExcelRowValue(row, "Ghi chú (Tùy chọn)", "Ghi chú", "Note", "Description");

        if (!supplierCodeOrName && !materialCodeOrName && !quantityStr) {
            continue;
        }

        // Match supplier
        const cleanSupp = supplierCodeOrName.toLowerCase().trim();
        const matchedSupplier = options.suppliers.find(
            (s) =>
                (s.code && s.code.toLowerCase().trim() === cleanSupp) ||
                (s.name && s.name.toLowerCase().trim() === cleanSupp) ||
                (s.id && String(s.id).toLowerCase().trim() === cleanSupp)
        ) || options.suppliers[0];

        if (!matchedSupplier) {
            throw new Error(`Dòng ${rowNum}: Nhà cung cấp '${supplierCodeOrName}' không tồn tại trong hệ thống.`);
        }

        // Match location
        const cleanLoc = locationCodeOrName.toLowerCase().trim();
        const matchedLocation = options.locations.find(
            (l) =>
                (l.code && l.code.toLowerCase().trim() === cleanLoc) ||
                (l.name && l.name.toLowerCase().trim() === cleanLoc) ||
                (l.id && String(l.id).toLowerCase().trim() === cleanLoc)
        ) || options.locations[0];

        if (!matchedLocation) {
            throw new Error(`Dòng ${rowNum}: Kho nhận '${locationCodeOrName}' không tồn tại trong hệ thống.`);
        }

        // Match material
        const cleanMat = materialCodeOrName.toLowerCase().trim();
        const matchedMaterial = options.materials.find(
            (m) =>
                (m.skuCode && m.skuCode.toLowerCase().trim() === cleanMat) ||
                (m.name && m.name.toLowerCase().trim() === cleanMat) ||
                (m.id && String(m.id).toLowerCase().trim() === cleanMat)
        );

        if (!matchedMaterial) {
            throw new Error(`Dòng ${rowNum}: Nguyên liệu '${materialCodeOrName}' không tồn tại hoặc không thuộc nhà cung cấp này.`);
        }

        // Validate quantity
        const quantity = Number(String(quantityStr).replace(/,/g, ""));
        if (isNaN(quantity) || quantity <= 0) {
            throw new Error(`Dòng ${rowNum}: Số lượng đặt phải là số lớn hơn 0.`);
        }

        itemsToCreate.push({
            supplierId: String(matchedSupplier.id),
            locationId: String(matchedLocation.id),
            purchaseOrderCode: poCode || `PO-${Date.now().toString().slice(-6)}`,
            materialId: String(matchedMaterial.id),
            orderedQuantity: quantity,
            supplierLot: supplierLot || "",
            note: note || ""
        });
    }

    if (itemsToCreate.length === 0) {
        throw new Error("Không có dữ liệu hợp lệ để nhập vào hệ thống.");
    }

    // Group by supplierId + locationId + purchaseOrderCode
    const groups = new Map<string, ParsedItem[]>();
    for (const item of itemsToCreate) {
        const groupKey = `${item.supplierId}__${item.locationId}__${item.purchaseOrderCode}`;
        if (!groups.has(groupKey)) {
            groups.set(groupKey, []);
        }
        groups.get(groupKey)!.push(item);
    }

    // Create receipts for each group
    for (const [, groupItems] of groups.entries()) {
        const first = groupItems[0];
        await goodsReceiptApi.createGoodsReceipt({
            supplierId: first.supplierId,
            receivingLocationId: first.locationId,
            purchaseOrderCode: first.purchaseOrderCode,
            receivedAt: new Date().toISOString().split("T")[0],
            deliveryPerson: "Đại diện giao hàng",
            note: first.note || "Nhập từ file Excel",
            outsidePo: false,
            submit: false,
            evidence: [],
            lines: groupItems.map((gi) => ({
                materialId: gi.materialId,
                orderedQuantity: gi.orderedQuantity,
                receivedQuantity: 0,
                initialDamagedQuantity: 0,
                supplierLot: gi.supplierLot,
                manufacturedAt: new Date().toISOString().split("T")[0],
                expiryDate: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split("T")[0],
                countryOfOrigin: "Việt Nam",
                qualityCertificate: "",
                storageCondition: "Nơi khô ráo, thoáng mát"
            }))
        });
    }
}
