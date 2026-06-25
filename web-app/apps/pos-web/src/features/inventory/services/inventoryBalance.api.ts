import { apiClient } from "../../../api/api-client";
import type {
    EntityId,
    InventoryBalanceQuery,
    InventoryBalanceResponse,
    InventoryBalanceViewResponse,
    PageResponse
} from "../types/inventory.types";

export const inventoryBalanceApi = {
    list(query: InventoryBalanceQuery = {}) {
        return apiClient.get<PageResponse<InventoryBalanceResponse>>(
            "/inventory/inventory-balances",
            { query }
        );
    },
    view(query: InventoryBalanceQuery = {}) {
        return apiClient.get<PageResponse<InventoryBalanceViewResponse>>(
            "/inventory/inventory-balances/view",
            { query }
        );
    },
    detail(id: EntityId) {
        return apiClient.get<InventoryBalanceResponse>(
            `/inventory/inventory-balances/${id}`
        );
    },
    listByInventoryItem(
        inventoryItemId: EntityId,
        page = 0,
        size = 20
    ) {
        return apiClient.get<PageResponse<InventoryBalanceResponse>>(
            `/inventory/inventory-items/${inventoryItemId}/balances`,
            { query: { page, size } }
        );
    }
};
