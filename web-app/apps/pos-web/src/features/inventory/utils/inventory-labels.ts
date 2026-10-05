import type {
    InventoryItemType,
    InventoryLocationType,
    InventoryReferenceType,
    InventoryStatus,
    InventoryTransactionType
} from "../types/inventory.types";
import type { DisposalRequest } from "../types/quality.types";

export function isCustomerReturnDisposal(request: DisposalRequest | null | undefined): boolean {
    return request?.sourceType === "CUSTOMER_RETURN_INSPECTION"
        || (!request?.sourceType && Boolean(request?.sourceInspectionId));
}

export const locationTypeLabels: Record<InventoryLocationType, string> = {
    WAREHOUSE: "Cửa hàng",
    STORAGE: "Kho chứa",
    DAMAGED: "Khu hàng lỗi",
    SHELF: "Kệ hàng",
    QUARANTINE: "Khu cách ly"
};

export const inventoryStatusLabels: Record<InventoryStatus, string> = {
    ACTIVE: "Đang hoạt động",
    INACTIVE: "Ngừng hoạt động"
};

export const inventoryItemTypeLabels: Record<InventoryItemType, string> = {
    PRODUCT_VARIANT: "Mặt hàng",
    INGREDIENT: "Nguyên liệu",
    PACKAGING: "Bao bì",
    COMBO: "Combo"
};

export const transactionTypeLabels: Record<
    InventoryTransactionType,
    string
> = {
    STOCK_IN: "Nhập hàng",
    SALE_OUT: "Bán hàng",
    RETURN_IN: "Hoàn hàng nhập lại",
    ADJUSTMENT: "Điều chỉnh tồn kho",
    TRANSFER: "Chuyển vị trí"
};

export const referenceTypeLabels: Record<
    InventoryReferenceType,
    string
> = {
    MANUAL: "Thao tác thủ công",
    ORDER: "Đơn hàng",
    RETURN_ORDER: "Đơn hoàn trả",
    STOCK_TRANSFER: "Chuyển tồn kho",
    STOCK_COUNT: "Kiểm kê tồn kho",
    PURCHASE_ORDER: "Đơn mua hàng",
    PURCHASE_RECEIPT: "Phiếu nhập mua",
    PRODUCTION_ORDER: "Lệnh sản xuất",
    CUSTOMER_RETURN: "Khách trả hàng",
    SUPPLIER_RETURN: "Trả nhà cung cấp",
    INVENTORY_DOCUMENT: "Chứng từ kho"
};

/** Loại tham chiếu được chọn khi tạo giao dịch thủ công — các loại còn lại do hệ thống sinh. */
export const manualReferenceTypes: InventoryReferenceType[] = [
    "MANUAL",
    "ORDER",
    "RETURN_ORDER",
    "STOCK_TRANSFER",
    "STOCK_COUNT"
];

export function formatReferenceType(value?: string | null) {
    if (!value) return "-";
    return referenceTypeLabels[value as InventoryReferenceType] ?? value;
}

export function formatInventoryDate(value: string) {
    return new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short"
    }).format(new Date(value));
}

export function formatQuantity(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        maximumFractionDigits: 3
    }).format(value);
}
