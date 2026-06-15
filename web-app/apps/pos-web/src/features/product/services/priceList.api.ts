import { apiClient } from "../../../api/api-client";
import type {
    CatalogQuery,
    PageResult,
    PriceList,
    PriceListInput
} from "../types/product.types";

export interface PriceListQuery extends CatalogQuery {
    branchId?: string;
    effectiveAt?: string;
}

export const priceListApi = {
    list(query: PriceListQuery = {}) {
        return apiClient.get<PageResult<PriceList>>("/inventory/price-lists", {
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
            `/inventory/price-lists/${encodeURIComponent(id)}`
        );
    },
    create(input: PriceListInput) {
        return apiClient.post<PriceList, PriceListInput>(
            "/inventory/price-lists",
            input
        );
    },
    update(id: string, input: PriceListInput) {
        return apiClient.put<PriceList, PriceListInput>(
            `/inventory/price-lists/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: PriceList["status"]) {
        return apiClient.patch<PriceList, { status: PriceList["status"] }>(
            `/inventory/price-lists/${encodeURIComponent(id)}/status`,
            { status }
        );
    },
    // Backend hiện cập nhật toàn bộ items qua PUT /inventory/price-lists/{id}.
};
