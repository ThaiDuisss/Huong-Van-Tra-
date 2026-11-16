import { keepPreviousData, useInfiniteQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";

import { posProductApi } from "../services/posProduct.api";
import type { SaleProductPreview } from "../types/sale.types";

const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_PAGE_SIZE = 20;

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
    /** Tổng số mặt hàng khớp từ khoá (kể cả trang chưa tải). */
    total: number;
    hasMore: boolean;
    loadingMore: boolean;
    loadMore(): void;
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

    const query = useInfiniteQuery({
        queryKey: productSearchQueryKey(debounced),
        queryFn: ({ pageParam }) =>
            posProductApi.searchPage(debounced, pageParam, SEARCH_PAGE_SIZE),
        initialPageParam: 0,
        getNextPageParam: (lastPage) =>
            lastPage.last || lastPage.content.length === 0
                ? undefined
                : lastPage.page + 1,
        enabled: active,
        placeholderData: keepPreviousData
    });

    const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;

    // Gộp các trang, bỏ trùng, ưu tiên mặt hàng còn bán được lên đầu
    // (sắp xếp ổn định nên thứ tự gốc của từng nhóm được giữ nguyên).
    const products = useMemo(() => {
        const seen = new Set<string>();
        const merged: SaleProductPreview[] = [];

        for (const page of query.data?.pages ?? []) {
            for (const product of page.content) {
                const key = product.productVariantId ?? product.id;
                if (!seen.has(key)) {
                    seen.add(key);
                    merged.push(product);
                }
            }
        }

        return [
            ...merged.filter((product) => product.sellableQuantity > 0),
            ...merged.filter((product) => product.sellableQuantity <= 0)
        ];
    }, [query.data]);

    const loadMore = useCallback(() => {
        if (hasNextPage && !isFetchingNextPage) {
            void fetchNextPage();
        }
    }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

    if (!enabled || normalized.length === 0) {
        return {
            products: [],
            loading: false,
            error: null,
            total: 0,
            hasMore: false,
            loadingMore: false,
            loadMore
        };
    }

    const pages = query.data?.pages;

    return {
        products,
        loading:
            debounced !== normalized ||
            (query.isFetching && !isFetchingNextPage),
        error: query.error,
        total: pages?.[pages.length - 1]?.totalElements ?? products.length,
        hasMore: Boolean(hasNextPage),
        loadingMore: isFetchingNextPage,
        loadMore
    };
}
