import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import type { CustomerReturnInspectionListQuery } from "../types/customer-return-inspection.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export function useCustomerReturnQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();
    const query = useMemo<CustomerReturnInspectionListQuery>(
        () => ({
            orderCode: searchParams.get("orderCode") ?? "",
            page: numberParam(searchParams.get("page"), 1) - 1,
            returnCode: searchParams.get("returnCode") ?? "",
            size: numberParam(searchParams.get("size"), 10),
            status: oneOf(searchParams.get("status"), ["ALL", "CREATED", "POSTED", "CANCELLED"], "ALL")
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<CustomerReturnInspectionListQuery>) {
        const merged = { ...query, ...patch };
        const next = new URLSearchParams();
        Object.entries(merged).forEach(([key, value]) => {
            const serialized = key === "page" ? String(Number(value) + 1) : String(value);
            if (serialized && serialized !== "ALL") next.set(key, serialized);
        });
        setSearchParams(next, { replace: true });
    }

    function resetQuery() {
        setSearchParams(new URLSearchParams(), { replace: true });
    }

    return { query, resetQuery, setQuery };
}
