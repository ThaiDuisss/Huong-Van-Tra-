import { apiClient } from "../../../api/api-client";
import type {
    InventoryLocationQuery,
    InventoryLocationRequest,
    InventoryLocationResponse,
    InventoryStatus,
    PageResponse
} from "../types/inventory.types";

export const inventoryLocationApi = {
    list(query: InventoryLocationQuery = {}) {
        return apiClient.get<PageResponse<InventoryLocationResponse>>(
            "/inventory/inventory-locations",
            { query }
        );
    },
    detail(id: number) {
        return apiClient.get<InventoryLocationResponse>(
            `/inventory/inventory-locations/${id}`
        );
    },
    create(input: InventoryLocationRequest) {
        return apiClient.post<
            InventoryLocationResponse,
            InventoryLocationRequest
        >("/inventory/inventory-locations", input);
    },
    update(id: number, input: InventoryLocationRequest) {
        return apiClient.put<
            InventoryLocationResponse,
            InventoryLocationRequest
        >(`/inventory/inventory-locations/${id}`, input);
    },
    updateStatus(id: number, status: InventoryStatus) {
        return apiClient.patch<
            InventoryLocationResponse,
            { status: InventoryStatus }
        >(`/inventory/inventory-locations/${id}/status`, { status });
    }
};
