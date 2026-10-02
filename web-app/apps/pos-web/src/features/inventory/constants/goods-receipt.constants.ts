import type {
    CostStatus,
    GoodsReceiptQcReasonCode,
    GoodsReceiptStatus,
    QcResult
} from "../types/goods-receipt.types";
import type { AppRole } from "../../../auth/role-utils";

export const goodsReceiptStatusLabels: Record<GoodsReceiptStatus, string> = {
    DRAFT: "Bản nháp",
    PENDING_APPROVAL: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    REJECTED: "Từ chối",
    RECEIVING: "Đang tiếp nhận",
    PENDING_EXCEPTION_APPROVAL: "Chờ duyệt ngoại lệ",
    POSTED: "Đã nhập kho",
    CANCELLED: "Đã hủy"
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

export const goodsReceiptRoleLabels: Record<AppRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    INVENTORY_MANAGER: "Quản lý kho",
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
    "PENDING_EXCEPTION_APPROVAL",
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
