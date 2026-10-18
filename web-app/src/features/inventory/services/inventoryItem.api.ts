import { apiClient } from "../../../api/api-client";
import type {
    InventoryItemQuery,
    InventoryItemResponse,
    PageResponse
} from "../types/inventory.types";

export const inventoryItemApi = {
    list(query: InventoryItemQuery = {}) {
        return apiClient.get<PageResponse<InventoryItemResponse>>(
            "/inventory/inventory-items",
            { query }
        );
    },
    detail(id: number) {
        return apiClient.get<InventoryItemResponse>(
            `/inventory/inventory-items/${id}`
        );
    }
};
