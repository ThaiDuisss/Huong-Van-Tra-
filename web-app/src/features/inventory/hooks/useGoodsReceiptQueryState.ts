import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";

import {
    costStatusOptions,
    goodsReceiptStatusOptions,
    qcResultOptions
} from "../constants/goods-receipt.constants";
import type { GoodsReceiptListQuery } from "../types/goods-receipt.types";

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

export const defaultGoodsReceiptQuery: GoodsReceiptListQuery = {
    costStatus: "ALL",
    hasVariance: "ALL",
    page: 0,
    purchaseOrderCode: "",
    qcResult: "ALL",
    receiptCode: "",
    receivedFrom: "",
    receivedTo: "",
    receivingLocationId: "ALL",
    size: 10,
    status: "ALL",
    supplierId: "ALL"
};

export function useGoodsReceiptQueryState() {
    const [searchParams, setSearchParams] = useSearchParams();

    const query = useMemo<GoodsReceiptListQuery>(
        () => ({
            costStatus: oneOf(
                searchParams.get("costStatus"),
                ["ALL", ...costStatusOptions],
                "ALL"
            ),
            hasVariance: oneOf(
                searchParams.get("hasVariance"),
                ["ALL", "YES", "NO"],
                "ALL"
            ),
            page: numberParam(searchParams.get("page"), 1) - 1,
            purchaseOrderCode: searchParams.get("purchaseOrderCode") ?? "",
            qcResult: oneOf(
                searchParams.get("qcResult"),
                ["ALL", ...qcResultOptions],
                "ALL"
            ),
            receiptCode: searchParams.get("receiptCode") ?? "",
            receivedFrom: searchParams.get("receivedFrom") ?? "",
            receivedTo: searchParams.get("receivedTo") ?? "",
            receivingLocationId:
                searchParams.get("receivingLocationId") ?? "ALL",
            size: numberParam(searchParams.get("size"), 10),
            status: oneOf(
                searchParams.get("status"),
                ["ALL", ...goodsReceiptStatusOptions],
                "ALL"
            ),
            supplierId: searchParams.get("supplierId") ?? "ALL"
        }),
        [searchParams]
    );

    function setQuery(patch: Partial<GoodsReceiptListQuery>) {
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
