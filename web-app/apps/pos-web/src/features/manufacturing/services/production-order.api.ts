import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../../product/types/product.types";
import type {
    ProductionOrder,
    ProductionOrderCompleteInput,
    ProductionOrderCreateInput,
    ProductionOrderQuery
} from "../types/production-order.types";

export const productionOrderApi = {
    getStatuses() {
        return apiClient.get<string[]>("/inventory/production-orders/statuses");
    },
    list(query: ProductionOrderQuery = {}) {
        return apiClient.get<PageResult<ProductionOrder>>("/inventory/production-orders", {
            query: {
                status: query.status,
                productVariantId: query.productVariantId,
                search: query.search,
                page: query.page,
                size: query.size
            }
        });
    },
    detail(id: string) {
        return apiClient.get<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}`);
    },
    create(input: ProductionOrderCreateInput) {
        return apiClient.post<ProductionOrder, ProductionOrderCreateInput>(
            "/inventory/production-orders",
            input
        );
    },
    submit(id: string) {
        return apiClient.post<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}/submit`);
    },
    approve(id: string) {
        return apiClient.post<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}/approve`);
    },
    reject(id: string) {
        return apiClient.post<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}/reject`);
    },
    cancel(id: string, reason: string) {
        return apiClient.post<ProductionOrder, { reason: string }>(
            `/inventory/production-orders/${encodeURIComponent(id)}/cancel`,
            { reason }
        );
    }
};
