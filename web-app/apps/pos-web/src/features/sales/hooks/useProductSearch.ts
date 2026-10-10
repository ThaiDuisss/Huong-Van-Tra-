import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";

import { posProductApi } from "../services/posProduct.api";
import type { SaleProductPreview } from "../types/sale.types";

const SEARCH_DEBOUNCE_MS = 300;

export const productSearchQueryKey = (keyword: string) =>
    ["pos", "products", "search", keyword] as const;

function useDebouncedValue<T>(value: T, delayMs: number): T {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            setDebounced(value);
        }, delayMs);

        return () => window.clearTimeout(timeoutId);
    }, [delayMs, value]);

    return debounced;
}

export interface ProductSearchResult {
    products: SaleProductPreview[];
    /** Đang gõ (chờ debounce) hoặc đang gọi API. */
    loading: boolean;
    error: unknown;
}

/**
 * Tìm mặt hàng cho POS qua TanStack Query.
 *
 * - Debounce 300ms như trước, nhưng kết quả được cache theo từ khoá: gõ lại
 *   từ khoá cũ hiện ngay, và khi mất mạng vẫn còn kết quả đã tìm (kết hợp với
 *   service worker cache API danh mục).
 * - keepPreviousData: đổi từ khoá không làm danh sách nháy về rỗng.
 */
export function useProductSearch(
    keyword: string,
    enabled = true
): ProductSearchResult {
    const normalized = keyword.trim();
    const debounced = useDebouncedValue(normalized, SEARCH_DEBOUNCE_MS);
    const active = enabled && debounced.length > 0;

    const query = useQuery({
        queryKey: productSearchQueryKey(debounced),
        queryFn: () => posProductApi.search(debounced),
        enabled: active,
        placeholderData: keepPreviousData
    });

    if (!enabled || normalized.length === 0) {
        return { products: [], loading: false, error: null };
    }

    return {
        products: query.data ?? [],
        loading: debounced !== normalized || query.isFetching,
        error: query.error
    };
}
