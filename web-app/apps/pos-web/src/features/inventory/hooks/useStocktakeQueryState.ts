import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import { stocktakeStatusOptions, stocktakeTypeOptions } from "../constants/stocktake.constants";
import type { StocktakeListQuery } from "../types/stocktake.types";

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(value: string | null, options: T[], fallback: T) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export const defaultStocktakeQuery: StocktakeListQuery = {
    code: "",
    hasVariance: "ALL",
    locationId: "ALL",
    ownerId: "ALL",
    page: 0,
    size: 10,
    startedFrom: "",
    startedTo: "",
    status: "ALL",
    type: "ALL"
};

export function useStocktakeQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();
    const query = useMemo<StocktakeListQuery>(
        () => ({
            code: searchParams.get("code") ?? "",
            hasVariance: oneOf(searchParams.get("hasVariance"), ["ALL", "YES", "NO"], "ALL"),
            locationId: searchParams.get("locationId") ?? "ALL",
            ownerId: searchParams.get("ownerId") ?? "ALL",
            page: numberParam(searchParams.get("page"), 1) - 1,
            size: numberParam(searchParams.get("size"), 10),
            startedFrom: searchParams.get("startedFrom") ?? "",
            startedTo: searchParams.get("startedTo") ?? "",
            status: oneOf(searchParams.get("status"), ["ALL", ...stocktakeStatusOptions], "ALL"),
            type: oneOf(searchParams.get("type"), ["ALL", ...stocktakeTypeOptions], "ALL")
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<StocktakeListQuery>) {
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
