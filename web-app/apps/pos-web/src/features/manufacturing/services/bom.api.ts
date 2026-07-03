import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../../product/types/product.types";
import type { BomResponse, BomInput, BomQuery } from "../types/bom.types";

export const bomApi = {
    list(query: BomQuery = {}) {
        return apiClient.get<PageResult<BomResponse>>("/catalog/boms", {
            query: {
                page: query.page,
                size: query.size,
                search: query.search,
                productId: query.productId,
                productVariantId: query.productVariantId,
                status: query.status
            }
        });
    },
    detail(id: string) {
        return apiClient.get<BomResponse>(`/catalog/boms/${encodeURIComponent(id)}`);
    },
    create(input: BomInput) {
        return apiClient.post<BomResponse, BomInput>("/catalog/boms", input);
    },
    update(id: string, input: BomInput) {
        return apiClient.put<BomResponse, BomInput>(
            `/catalog/boms/${encodeURIComponent(id)}`,
            input
        );
    },
    updateStatus(id: string, status: BomResponse["status"]) {
        return apiClient.patch<BomResponse, { status: BomResponse["status"] }>(
            `/catalog/boms/${encodeURIComponent(id)}/status`,
            { status }
        );
    }
};
