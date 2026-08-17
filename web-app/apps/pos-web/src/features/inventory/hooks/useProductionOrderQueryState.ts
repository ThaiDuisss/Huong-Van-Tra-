import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { productionStatusOptions } from "../constants/production-order.constants";
import type { ProductionOrderListQuery } from "../types/production-order.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export function useProductionOrderQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();
    const query = useMemo<ProductionOrderListQuery>(
        () => ({
            code: searchParams.get("code") ?? "",
            createdById: searchParams.get("createdById") ?? "ALL",
            createdFrom: searchParams.get("createdFrom") ?? "",
            createdTo: searchParams.get("createdTo") ?? "",
            dueFrom: searchParams.get("dueFrom") ?? "",
            dueTo: searchParams.get("dueTo") ?? "",
            hasShortage: oneOf(
                searchParams.get("hasShortage"),
                ["ALL", "YES", "NO"],
                "ALL"
            ),
            locationId: searchParams.get("locationId") ?? "ALL",
            outputSkuId: searchParams.get("outputSkuId") ?? "ALL",
            page: numberParam(searchParams.get("page"), 1) - 1,
            size: numberParam(searchParams.get("size"), 10),
            status: oneOf(
                searchParams.get("status"),
                ["ALL", ...productionStatusOptions],
                "ALL"
            )
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<ProductionOrderListQuery>) {
        const merged = { ...query, ...patch };
        const next = new URLSearchParams();
        Object.entries(merged).forEach(([key, value]) => {
            const serialized =
                key === "page" ? String(Number(value) + 1) : String(value);
            if (serialized && serialized !== "ALL") next.set(key, serialized);
        });
        setSearchParams(next, { replace: true });
    }

    function resetQuery() {
        setSearchParams(new URLSearchParams(), { replace: true });
    }

    return { query, resetQuery, setQuery };
}
