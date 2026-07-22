import { apiClient } from "../../../api/api-client";
import type {
    EntityId,
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
    lookup(search: string = "", page: number = 0, size: number = 20) {
        return apiClient.get<PageResponse<{ id: string; name: string }>>(
            "/inventory/inventory-locations/store/lookup",
            { query: { search, page, size } }
        );
    },
    detail(id: EntityId) {
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
    update(id: EntityId, input: InventoryLocationRequest) {
        return apiClient.put<
            InventoryLocationResponse,
            InventoryLocationRequest
        >(`/inventory/inventory-locations/${id}`, input);
    },
    updateStatus(id: EntityId, status: InventoryStatus) {
        return apiClient.patch<
            InventoryLocationResponse,
            { status: InventoryStatus }
        >(`/inventory/inventory-locations/${id}/status`, { status });
    },
    defaultWarehouse(branchId: EntityId) {
        return apiClient.get<InventoryLocationResponse>(
            "/inventory/inventory-locations/default-warehouse",
            { query: { branchId } }
        );
    }
};
