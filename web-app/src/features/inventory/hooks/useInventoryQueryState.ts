import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import type {
    InventoryManagementQuery,
    InventoryRecordStatus,
    InventorySortField
} from "../types/inventory-management.types";

const sortFields: InventorySortField[] = [
    "updatedAt",
    "code",
    "quantity",
    "value"
];

const statuses: Array<"ALL" | InventoryRecordStatus> = [
    "ALL",
    "DRAFT",
    "PENDING_APPROVAL",
    "APPROVED",
    "IN_PROGRESS",
    "COMPLETED",
    "REJECTED",
    "CANCELLED",
    "WARNING",
    "BLOCKED"
];

function oneOf<T extends string>(
    value: string | null,
    allowed: T[],
    fallback: T
) {
    return value && allowed.includes(value as T) ? (value as T) : fallback;
}

function positiveNumber(value: string | null, fallback: number) {
    const numberValue = Number(value);

    return Number.isFinite(numberValue) && numberValue > 0
        ? numberValue
        : fallback;
}

export function useInventoryQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();

    const query = useMemo<InventoryManagementQuery>(
        () => ({
            locationId: searchParams.get("locationId") ?? "ALL",
            page: positiveNumber(searchParams.get("page"), 1) - 1,
            search: searchParams.get("search") ?? "",
            size: positiveNumber(searchParams.get("size"), 10),
            sortBy: oneOf(
                searchParams.get("sortBy"),
                sortFields,
                "updatedAt"
            ),
            sortDirection: oneOf(
                searchParams.get("sortDirection"),
                ["asc", "desc"],
                "desc"
            ),
            status: oneOf(searchParams.get("status"), statuses, "ALL")
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<InventoryManagementQuery>) {
        const next = new URLSearchParams(searchParams);
        const merged = { ...query, ...patch };

        next.set("page", String(merged.page + 1));
        next.set("size", String(merged.size));
        next.set("search", merged.search);
        next.set("status", merged.status);
        next.set("locationId", merged.locationId);
        next.set("sortBy", merged.sortBy);
        next.set("sortDirection", merged.sortDirection);

        for (const [key, value] of next.entries()) {
            if (value === "" || value === "ALL") {
                next.delete(key);
            }
        }

        setSearchParams(next, { replace: true });
    }

    return { query, setQuery };
}
