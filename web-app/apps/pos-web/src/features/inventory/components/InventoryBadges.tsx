import type {
    InventoryLocationType,
    InventoryStatus,
    InventoryTransactionType
} from "../types/inventory.types";
import {
    inventoryStatusLabels,
    locationTypeLabels,
    transactionTypeLabels
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
        SHELF: "bg-emerald-50 text-emerald-700",
        COUNTER: "bg-blue-50 text-blue-700",
        STORAGE: "bg-amber-50 text-amber-700",
        DAMAGED: "bg-red-50 text-red-700"
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
    const tone =
        type === "SALE_OUT"
            ? "bg-red-50 text-red-700"
            : type === "ADJUSTMENT"
              ? "bg-amber-50 text-amber-700"
              : "bg-emerald-50 text-emerald-700";

    return (
        <span className={`rounded-lg px-2.5 py-1 text-xs font-bold ${tone}`}>
            {transactionTypeLabels[type]}
        </span>
    );
}
