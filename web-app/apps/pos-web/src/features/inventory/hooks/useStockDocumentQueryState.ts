import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import {
    stockDocumentStatusOptions,
    stockDocumentTypeOptions
} from "../constants/stock-document.constants";
import type {
    StockDocumentDirection,
    StockDocumentListQuery
} from "../types/stock-document.types";

const directions: StockDocumentDirection[] = ["ALL", "IN", "OUT"];
const hasBatchOptions: StockDocumentListQuery["hasBatch"][] = [
    "ALL",
    "YES",
    "NO"
];

function numberParam(value: string | null, fallback: number) {
    const parsed = Number(value);

    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function oneOf<T extends string>(
    value: string | null,
    options: T[],
    fallback: T
) {
    return value && options.includes(value as T) ? (value as T) : fallback;
}

export const defaultStockDocumentQuery: StockDocumentListQuery = {
    createdById: "ALL",
    createdFrom: "",
    createdTo: "",
    destinationLocationId: "ALL",
    direction: "ALL",
    documentCode: "",
    hasBatch: "ALL",
    locationId: "ALL",
    page: 0,
    postedFrom: "",
    postedTo: "",
    referenceCode: "",
    size: 10,
    sourceLocationId: "ALL",
    status: "ALL",
    type: "ALL"
};

export function useStockDocumentQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();

    const query = useMemo<StockDocumentListQuery>(
        () => ({
            createdById: searchParams.get("createdById") ?? "ALL",
            createdFrom: searchParams.get("createdFrom") ?? "",
            createdTo: searchParams.get("createdTo") ?? "",
            destinationLocationId:
                searchParams.get("destinationLocationId") ?? "ALL",
            direction: oneOf(
                searchParams.get("direction"),
                directions,
                "ALL"
            ),
            documentCode: searchParams.get("documentCode") ?? "",
            hasBatch: oneOf(
                searchParams.get("hasBatch"),
                hasBatchOptions,
                "ALL"
            ),
            locationId: searchParams.get("locationId") ?? "ALL",
            page: numberParam(searchParams.get("page"), 1) - 1,
            postedFrom: searchParams.get("postedFrom") ?? "",
            postedTo: searchParams.get("postedTo") ?? "",
            referenceCode: searchParams.get("referenceCode") ?? "",
            size: numberParam(searchParams.get("size"), 10),
            sourceLocationId: searchParams.get("sourceLocationId") ?? "ALL",
            status: oneOf(
                searchParams.get("status"),
                ["ALL", ...stockDocumentStatusOptions],
                "ALL"
            ),
            type: oneOf(
                searchParams.get("type"),
                ["ALL", ...stockDocumentTypeOptions],
                "ALL"
            )
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<StockDocumentListQuery>) {
        const merged = { ...query, ...patch };
        const next = new URLSearchParams();

        Object.entries(merged).forEach(([key, value]) => {
            const serialized =
                key === "page" ? String(Number(value) + 1) : String(value);

            if (serialized && serialized !== "ALL") {
                next.set(key, serialized);
            }
        });

        setSearchParams(next, { replace: true });
    }

    function resetQuery() {
        setSearchParams(new URLSearchParams(), { replace: true });
    }

    return { query, resetQuery, setQuery };
}
