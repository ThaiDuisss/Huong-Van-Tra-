import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { customerReturnReasons, customerReturnStatuses } from "../constants/customer-return.constants";
import type { CustomerReturnListQuery } from "../types/customer-return.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export function useCustomerReturnQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();
    const query = useMemo<CustomerReturnListQuery>(
        () => ({
            branchId: searchParams.get("branchId") ?? "ALL",
            code: searchParams.get("code") ?? "",
            customer: searchParams.get("customer") ?? "",
            orderCode: searchParams.get("orderCode") ?? "",
            page: numberParam(searchParams.get("page"), 1) - 1,
            reason: oneOf(searchParams.get("reason"), ["ALL", ...customerReturnReasons], "ALL"),
            requestedFrom: searchParams.get("requestedFrom") ?? "",
            requestedTo: searchParams.get("requestedTo") ?? "",
            size: numberParam(searchParams.get("size"), 10),
            status: oneOf(searchParams.get("status"), ["ALL", ...customerReturnStatuses], "ALL"),
            tab: oneOf(searchParams.get("tab"), ["MANAGER_REVIEW", "AWAITING_RECEIVE", "PENDING_QC", "RESTOCK", "DISPOSAL", "COMPLETED"], "MANAGER_REVIEW")
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<CustomerReturnListQuery>) {
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
