import { useState } from "react";

import type { CatalogStatus } from "../types/product.types";

export function useCatalogPageState() {
    const [query, setQuery] = useState("");
    const [status, setStatus] =
        useState<"ALL" | CatalogStatus>("ALL");
    const [secondaryValue, setSecondaryValue] = useState("ALL");
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    return {
        query,
        status,
        secondaryValue,
        page,
        pageSize,
        setQuery(value: string) {
            setQuery(value);
            setPage(1);
        },
        setStatus(value: "ALL" | CatalogStatus) {
            setStatus(value);
            setPage(1);
        },
        setSecondaryValue(value: string) {
            setSecondaryValue(value);
            setPage(1);
        },
        setPage,
        setPageSize(value: number) {
            setPageSize(value);
            setPage(1);
        }
    };
}
