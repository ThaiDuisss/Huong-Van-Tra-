import type {
    CostStatus,
    GoodsReceiptQcReasonCode,
    GoodsReceiptStatus,
    QcResult
} from "../types/goods-receipt.types";

export const goodsReceiptStatusLabels: Record<GoodsReceiptStatus, string> = {
    DRAFT: "Bản nháp",
    PENDING_APPROVAL: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    REJECTED: "Từ chối",
    RECEIVING: "Đang tiếp nhận",
    QC_PENDING: "Chờ kiểm định QC",
    QC_COMPLETED: "Chờ ghi sổ",
    POSTED: "Đã nhập kho",
    CANCELLED: "Đã hủy",
    AWAITING_COST: "Chờ xác nhận giá",
    AWAITING_QC: "Chờ QC",
    PARTIALLY_PASSED: "Đạt một phần",
    READY_TO_POST: "Chờ ghi sổ",
    SUBMITTED: "Đã gửi duyệt"
};

export const qcResultLabels: Record<QcResult, string> = {
    FAILED: "Không đạt",
    PARTIALLY_PASSED: "Đạt một phần",
    PASSED: "Đạt",
    PENDING: "Chờ QC"
};

export const costStatusLabels: Record<CostStatus, string> = {
    FINAL: "Giá cuối cùng",
    PENDING: "Chờ giá",
    PROVISIONAL: "Giá tạm tính"
};

export const goodsReceiptRoleLabels: Record<string, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    INVENTORY_STAFF: "Nhân viên kho",
    QC_STAFF: "Nhân viên QC"
};

export const qcReasonLabels: Record<GoodsReceiptQcReasonCode, string> = {
    "QLT-01": "Sai quy cách",
    "QLT-02": "Bao bì hỏng",
    "QLT-03": "Ẩm/mốc",
    "QLT-04": "Gần hết hạn",
    "QLT-05": "Sai số lô",
    "QLT-06": "Không đủ chứng từ",
    "QLT-99": "Khác"
};

export const goodsReceiptStatusOptions: GoodsReceiptStatus[] = [
    "DRAFT",
    "PENDING_APPROVAL",
    "APPROVED",
    "RECEIVING",
    "QC_PENDING",
    "QC_COMPLETED",
    "POSTED",
    "REJECTED",
    "CANCELLED",
    "SUBMITTED",
    "AWAITING_QC",
    "PARTIALLY_PASSED",
    "AWAITING_COST",
    "READY_TO_POST"
];

export const qcResultOptions: QcResult[] = [
    "PENDING",
    "PASSED",
    "PARTIALLY_PASSED",
    "FAILED"
];

export const costStatusOptions: CostStatus[] = [
    "PENDING",
    "PROVISIONAL",
    "FINAL"
];

export function calculateLineNetAmount(input: {
    acceptedQuantity: number;
    allocatedShippingCost: number;
    discount: number;
    otherCost: number;
    purchaseUnitPrice: number;
    tax: number;
}) {
    return (
        input.purchaseUnitPrice * input.acceptedQuantity -
        input.discount +
        input.tax +
        input.allocatedShippingCost +
        input.otherCost
    );
}

export function calculateFinalUnitCost(input: {
    acceptedQuantity: number;
    allocatedShippingCost: number;
    discount: number;
    otherCost: number;
    purchaseUnitPrice: number;
    tax: number;
}) {
    if (input.acceptedQuantity <= 0) {
        return 0;
    }

    return calculateLineNetAmount(input) / input.acceptedQuantity;
}
