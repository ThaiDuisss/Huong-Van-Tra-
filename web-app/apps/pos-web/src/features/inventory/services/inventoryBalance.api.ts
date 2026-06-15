import { apiClient } from "../../../api/api-client";
import type {
    InventoryBalanceQuery,
    InventoryBalanceResponse,
    PageResponse
} from "../types/inventory.types";

export const inventoryBalanceApi = {
    list(query: InventoryBalanceQuery = {}) {
        return apiClient.get<PageResponse<InventoryBalanceResponse>>(
            "/inventory/inventory-balances",
            { query }
        );
    },
    detail(id: number) {
        return apiClient.get<InventoryBalanceResponse>(
            `/inventory/inventory-balances/${id}`
        );
    },
    listByInventoryItem(
        inventoryItemId: number,
        page = 0,
        size = 20
    ) {
        return apiClient.get<PageResponse<InventoryBalanceResponse>>(
            `/inventory/inventory-items/${inventoryItemId}/balances`,
            { query: { page, size } }
        );
    }
};
