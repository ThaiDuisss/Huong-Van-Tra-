import type {
    GoodsReceiptLine,
    GoodsReceiptLineResolutionInput,
    ReceiptExcessResolution,
    ReceiptShortageResolution
} from "../types/goods-receipt.types";

export const EXCESS_RESOLUTION_LABELS: Record<ReceiptExcessResolution, string> = {
    ACCEPT_ALL: "Nhận toàn bộ số thực nhận",
    ACCEPT_ORDERED_ONLY: "Chỉ nhận đúng số đặt, trả lại phần thừa"
};

export const SHORTAGE_RESOLUTION_LABELS: Record<ReceiptShortageResolution, string> = {
    ACCEPT_PARTIAL_CLOSE: "Nhận số hiện có & đóng dòng",
    REJECT_LINE: "Từ chối nhận cả dòng, trả lại NCC"
};

/** Nhãn khi dòng giao đủ nhưng có hàng hỏng. */
export const DAMAGED_RESOLUTION_LABELS: Record<ReceiptShortageResolution, string> = {
    ACCEPT_PARTIAL_CLOSE: "Nhận phần đạt QC & đóng dòng",
    REJECT_LINE: "Từ chối nhận cả dòng, trả lại NCC"
};

export interface LineException {
    /** Số giao vượt số đặt. */
    excess: number;
    /** Số giao thiếu so với số đặt. */
    shortage: number;
    /** Số không đạt QC (không bao giờ nhập kho). */
    damaged: number;
}

export function getLineException(line: GoodsReceiptLine): LineException {
    const ordered = line.orderedQuantity ?? 0;
    return {
        damaged: line.qc.failedQuantity,
        excess: Math.max(0, line.receivedQuantity - ordered),
        shortage: Math.max(0, ordered - line.receivedQuantity)
    };
}

export function hasLineException(line: GoodsReceiptLine) {
    const exception = getLineException(line);
    return exception.excess > 0 || exception.shortage > 0 || exception.damaged > 0;
}

/** Số dự kiến ghi sổ của dòng theo cách xử lý đang chọn — khớp logic backend khi duyệt ngoại lệ. */
export function previewPostedQuantity(line: GoodsReceiptLine, resolution?: GoodsReceiptLineResolutionInput) {
    const accepted = line.qc.passedQuantity;
    const { excess } = getLineException(line);
    if (excess > 0 && resolution?.excessResolution === "ACCEPT_ORDERED_ONLY") {
        return Math.min(accepted, line.orderedQuantity ?? 0);
    }
    if (needsShortageResolution(line) && resolution?.shortageResolution === "REJECT_LINE") {
        return 0;
    }
    return accepted;
}

/**
 * Dòng cần chọn cách xử lý kiểu "thiếu": giao thiếu, hoặc giao đủ nhưng có hàng hỏng (số đạt QC < số đặt).
 * Khớp backend: dòng giao thừa chỉ chọn cách xử lý phần thừa.
 */
export function needsShortageResolution(line: GoodsReceiptLine) {
    const { excess, shortage, damaged } = getLineException(line);
    return excess === 0 && (shortage > 0 || damaged > 0);
}

/** Dòng ngoại lệ thừa / thiếu / hỏng đã chọn đủ cách xử lý chưa. */
export function isLineResolved(line: GoodsReceiptLine, resolution?: GoodsReceiptLineResolutionInput) {
    const { excess } = getLineException(line);
    if (excess > 0) return Boolean(resolution?.excessResolution);
    if (needsShortageResolution(line)) return Boolean(resolution?.shortageResolution);
    return true;
}
