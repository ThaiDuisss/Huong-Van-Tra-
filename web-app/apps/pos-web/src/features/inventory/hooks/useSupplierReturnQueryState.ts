import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { supplierReturnReasons, supplierReturnStatuses } from "../constants/supplier-return.constants";
import type { SupplierReturnListQuery } from "../types/supplier-return.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export function useSupplierReturnQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();
    const query = useMemo<SupplierReturnListQuery>(
        () => ({
            batchCode: searchParams.get("batchCode") ?? "",
            code: searchParams.get("code") ?? "",
            createdFrom: searchParams.get("createdFrom") ?? "",
            createdTo: searchParams.get("createdTo") ?? "",
            page: numberParam(searchParams.get("page"), 1) - 1,
            reason: oneOf(searchParams.get("reason"), ["ALL", ...supplierReturnReasons], "ALL"),
            receiptCode: searchParams.get("receiptCode") ?? "",
            size: numberParam(searchParams.get("size"), 10),
            status: oneOf(searchParams.get("status"), ["ALL", ...supplierReturnStatuses], "ALL"),
            supplierId: searchParams.get("supplierId") ?? "ALL"
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<SupplierReturnListQuery>) {
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
