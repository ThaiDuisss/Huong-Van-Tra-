import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    PageResult,
    PriceChangeHistory,
    PriceChangeHistoryQuery,
    PriceList,
    PriceListInput
} from "../types/product.types";

export interface PriceListQuery extends CatalogQuery {
    branchId?: string;
    effectiveAt?: string;
}

export const priceListApi = {
    list(query: PriceListQuery = {}) {
        return apiClient.get<PageResult<PriceList>>("/catalog/price-lists", {
            query: {
                page: query.page,
                size: query.size,
                branchId: query.branchId,
                status: query.status,
                effectiveAt: query.effectiveAt
            }
        });
    },
    detail(id: string) {
        return apiClient.get<PriceList>(
            `/catalog/price-lists/${encodeURIComponent(id)}`
        );
    },
    create(input: PriceListInput) {
        return apiClient.post<PriceList, PriceListInput>(
            "/catalog/price-lists",
            input
        );
    },
    update(id: string, input: PriceListInput) {
        return apiClient.put<PriceList, PriceListInput>(
            `/catalog/price-lists/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: PriceList["status"]) {
        return apiClient.patch<PriceList, { status: PriceList["status"] }>(
            `/catalog/price-lists/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    history(id: string, query: PriceChangeHistoryQuery = {}) {
        return apiClient.get<PageResult<PriceChangeHistory>>(
            `/catalog/price-lists/${encodeURIComponent(id)}/history`,
            {
                query: {
                    page: query.page,
                    size: query.size,
                    variantId: query.variantId,
                    changeType: query.changeType,
                    from: query.from,
                    to: query.to
                }
            }
        );
    },
    // Backend cập nhật toàn bộ items qua PUT /catalog/price-lists/{id}.
};
