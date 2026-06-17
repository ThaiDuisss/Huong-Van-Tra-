import { apiClient } from "../../../api/api-client";
import type {
    InventoryTransactionQuery,
    InventoryTransactionResponse,
    PageResponse,
    StockAdjustmentRequest,
    StockInRequest
} from "../types/inventory.types";

export const inventoryTransactionApi = {
    list(query: InventoryTransactionQuery = {}) {
        return apiClient.get<PageResponse<InventoryTransactionResponse>>(
            "/inventory/inventory-transactions",
            { query }
        );
    },
    detail(id: number) {
        return apiClient.get<InventoryTransactionResponse>(
            `/inventory/inventory-transactions/${id}`
        );
    },
    stockIn(input: StockInRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StockInRequest
        >("/inventory/inventory-transactions/stock-in", input);
    },
    adjust(input: StockAdjustmentRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StockAdjustmentRequest
        >("/inventory/inventory-transactions/adjustment", input);
    }
};
