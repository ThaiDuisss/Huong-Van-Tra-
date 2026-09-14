import { apiClient } from "../../../api/api-client";
import type { PageResult } from "../../product/types/product.types";
import type {
    BomOption,
    ProductionOrder,
    ProductionOrderCompleteInput,
    ProductionOrderCreateInput,
    ProductionOrderQuery
} from "../types/production-order.types";

export const productionOrderApi = {
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
    bomOptions(productVariantId: string) {
        return apiClient.get<BomOption[]>("/inventory/production-orders/bom-options", {
            query: { productVariantId }
        });
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
    release(id: string) {
        return apiClient.post<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}/release`);
    },
    start(id: string) {
        return apiClient.post<ProductionOrder>(`/inventory/production-orders/${encodeURIComponent(id)}/start`);
    },
    complete(id: string, input: ProductionOrderCompleteInput) {
        return apiClient.post<ProductionOrder, ProductionOrderCompleteInput>(
            `/inventory/production-orders/${encodeURIComponent(id)}/complete`,
            input
        );
    },
    cancel(id: string, reason: string) {
        return apiClient.post<ProductionOrder, { reason: string }>(
            `/inventory/production-orders/${encodeURIComponent(id)}/cancel`,
            { reason }
        );
    }
};
