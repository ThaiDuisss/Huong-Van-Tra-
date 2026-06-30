import { apiClient } from "../../../api/api-client";
import type {
    EntityId,
    InventoryLocationResponse,
    InventoryStatus,
    PageResponse,
    StoreShelfQuery,
    StoreShelfRequest
} from "../types/inventory.types";

/**
 * API client cho màn hình Quản lý kệ (Store Shelf).
 * Base path: /inventory/inventory-locations/store
 */
export const storeShelfApi = {
    list(query: StoreShelfQuery = {}) {
        return apiClient.get<PageResponse<InventoryLocationResponse>>(
            "/inventory/inventory-locations/store",
            { query }
        );
    },

    detail(id: EntityId) {
        return apiClient.get<InventoryLocationResponse>(
            `/inventory/inventory-locations/store/${id}`
        );
    },

    create(input: StoreShelfRequest) {
        return apiClient.post<InventoryLocationResponse, StoreShelfRequest>(
            "/inventory/inventory-locations/store",
            input
        );
    },

    update(id: EntityId, input: StoreShelfRequest) {
        return apiClient.put<InventoryLocationResponse, StoreShelfRequest>(
            `/inventory/inventory-locations/store/${id}`,
            input
        );
    },

    updateStatus(id: EntityId, status: InventoryStatus) {
        return apiClient.patch<InventoryLocationResponse, { status: InventoryStatus }>(
            `/inventory/inventory-locations/store/${id}/status`,
            { status }
        );
    }
};
