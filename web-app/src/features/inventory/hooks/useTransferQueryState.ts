import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { transferStatusOptions, transferTabs } from "../constants/transfer.constants";
import type { TransferListQuery } from "../types/transfer.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export const defaultTransferQuery: TransferListQuery = {
    code: "",
    destinationLocationId: "ALL",
    expectedFrom: "",
    expectedTo: "",
    hasDiscrepancy: "ALL",
    page: 0,
    requestedById: "ALL",
    requestedFrom: "",
    requestedTo: "",
    size: 10,
    sourceLocationId: "ALL",
    status: "ALL",
    tab: "ALL"
};

export function useTransferQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();

    const query = useMemo<TransferListQuery>(
        () => ({
            code: searchParams.get("code") ?? "",
            destinationLocationId: searchParams.get("destinationLocationId") ?? "ALL",
            expectedFrom: searchParams.get("expectedFrom") ?? "",
            expectedTo: searchParams.get("expectedTo") ?? "",
            hasDiscrepancy: oneOf(searchParams.get("hasDiscrepancy"), ["ALL", "YES", "NO"], "ALL"),
            page: numberParam(searchParams.get("page"), 1) - 1,
            requestedById: searchParams.get("requestedById") ?? "ALL",
            requestedFrom: searchParams.get("requestedFrom") ?? "",
            requestedTo: searchParams.get("requestedTo") ?? "",
            size: numberParam(searchParams.get("size"), 10),
            sourceLocationId: searchParams.get("sourceLocationId") ?? "ALL",
            status: oneOf(searchParams.get("status"), ["ALL", ...transferStatusOptions], "ALL"),
            tab: oneOf(searchParams.get("tab"), transferTabs, "ALL")
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<TransferListQuery>) {
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
