import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

import type {
    InventoryLocationType,
    InventoryStatus,
    InventoryTransactionType
} from "../types/inventory.types";
import {
    inventoryStatusLabels,
    locationTypeLabels
} from "../utils/inventory-labels";

export function InventoryStatusBadge({ status }: { status: InventoryStatus }) {
    return (
        <StatusBadge dot tone={status === "ACTIVE" ? "success" : "neutral"}>
            {inventoryStatusLabels[status]}
        </StatusBadge>
    );
}

const locationTone: Record<InventoryLocationType, BadgeTone> = {
    WAREHOUSE: "success",
    DAMAGED: "danger",
    SHELF: "info",
    QUARANTINE: "violet"
};

export function LocationTypeBadge({ type }: { type: InventoryLocationType }) {
    return (
        <StatusBadge shape="tag" tone={locationTone[type] ?? "neutral"}>
            {locationTypeLabels[type]}
        </StatusBadge>
    );
}

const transactionConfig: Record<InventoryTransactionType, { label: string; tone: BadgeTone }> = {
    STOCK_IN: { label: "Nhập kho", tone: "success" },
    SALE_OUT: { label: "Xuất bán", tone: "info" },
    RETURN_IN: { label: "Trả hàng", tone: "warning" },
    ADJUSTMENT: { label: "Kiểm kê", tone: "violet" },
    TRANSFER: { label: "Chuyển kho", tone: "neutral" }
};

export function TransactionTypeBadge({ type }: { type: InventoryTransactionType }) {
    const config = transactionConfig[type] ?? { label: type, tone: "neutral" as BadgeTone };

    return (
        <StatusBadge shape="tag" tone={config.tone}>
            {config.label}
        </StatusBadge>
    );
}
