import type {
    InventoryLocationType,
    InventoryStatus,
    InventoryTransactionType
} from "../types/inventory.types";
import {
    inventoryStatusLabels,
    locationTypeLabels
} from "../utils/inventory-labels";

export function InventoryStatusBadge({
    status
}: {
    status: InventoryStatus;
}) {
    return (
        <span
            className={`inline-flex rounded-lg px-2.5 py-1 text-xs font-bold ${
                status === "ACTIVE"
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-neutral-100 text-neutral-600"
            }`}
        >
            {inventoryStatusLabels[status]}
        </span>
    );
}

export function LocationTypeBadge({
    type
}: {
    type: InventoryLocationType;
}) {
    const tone = {
        WAREHOUSE: "bg-emerald-50 text-emerald-700",
        STORAGE: "bg-amber-50 text-amber-700",
        DAMAGED: "bg-red-50 text-red-700",
        SHELF: "bg-blue-50 text-blue-700"
    }[type];

    return (
        <span className={`rounded-lg px-2.5 py-1 text-xs font-bold ${tone}`}>
            {locationTypeLabels[type]}
        </span>
    );
}

export function TransactionTypeBadge({
    type
}: {
    type: InventoryTransactionType;
}) {
    const badgeConfig: Record<InventoryTransactionType, { label: string; tone: string }> = {
        STOCK_IN: { label: "Nhập kho", tone: "bg-emerald-50 text-emerald-700 border-emerald-200" },
        SALE_OUT: { label: "Xuất bán", tone: "bg-blue-50 text-blue-700 border-blue-200" },
        RETURN_IN: { label: "Trả hàng", tone: "bg-amber-50 text-amber-700 border-amber-200" },
        ADJUSTMENT: { label: "Kiểm kê", tone: "bg-purple-50 text-purple-700 border-purple-200" },
        TRANSFER: { label: "Chuyển kho", tone: "bg-gray-100 text-gray-700 border-gray-200" },
    };

    const config = badgeConfig[type] ?? { label: type, tone: "bg-gray-100 text-gray-700 border-gray-200" };

    return (
        <span className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-bold ${config.tone}`}>
            {config.label}
        </span>
    );
}
