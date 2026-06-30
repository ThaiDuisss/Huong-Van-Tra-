import type {
    InventoryItemType,
    InventoryLocationType,
    InventoryReferenceType,
    InventoryStatus,
    InventoryTransactionType
} from "../types/inventory.types";

export const locationTypeLabels: Record<InventoryLocationType, string> = {
    WAREHOUSE: "Cửa hàng",
    STORAGE: "Kho chứa",
    DAMAGED: "Khu hàng lỗi"
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
    STOCK_COUNT: "Kiểm kê tồn kho"
};

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
