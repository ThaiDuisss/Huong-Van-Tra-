import type {
    CostStatus,
    GoodsReceiptQcReasonCode,
    GoodsReceiptRole,
    GoodsReceiptStatus,
    QcResult
} from "../types/goods-receipt.types";

export const goodsReceiptStatusLabels: Record<GoodsReceiptStatus, string> = {
    AWAITING_COST: "Chờ xác nhận giá",
    AWAITING_QC: "Chờ QC",
    CANCELLED: "Đã hủy",
    DRAFT: "Nháp",
    PARTIALLY_PASSED: "Đạt một phần",
    POSTED: "Đã post",
    READY_TO_POST: "Sẵn sàng post",
    RECEIVING: "Đang tiếp nhận",
    REJECTED: "Từ chối",
    SUBMITTED: "Đã submit"
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

export const goodsReceiptRoleLabels: Record<GoodsReceiptRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    QC_STAFF: "Nhân viên QC",
    WAREHOUSE: "Nhân viên kho"
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
    "SUBMITTED",
    "RECEIVING",
    "AWAITING_QC",
    "PARTIALLY_PASSED",
    "AWAITING_COST",
    "READY_TO_POST",
    "POSTED",
    "REJECTED",
    "CANCELLED"
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
