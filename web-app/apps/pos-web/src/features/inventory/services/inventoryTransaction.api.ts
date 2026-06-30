import { apiClient } from "../../../api/api-client";
import type {
    EntityId,
    InventoryTransactionQuery,
    InventoryTransactionResponse,
    PageResponse,
    StockAdjustmentRequest,
    StockInRequest,
    StoreStockAdjustmentRequest,
    StoreStockInRequest,
    StoreStockOutRequest,
    StoreTransferRequest
} from "../types/inventory.types";

export const inventoryTransactionApi = {
    list(query: InventoryTransactionQuery = {}) {
        return apiClient.get<PageResponse<InventoryTransactionResponse>>(
            "/inventory/inventory-transactions",
            { query }
        );
    },
    /** Biến động kho dành cho Cửa hàng: chỉ SHELF thuộc WAREHOUSE đầu tiên */
    storeList(query: InventoryTransactionQuery = {}) {
        return apiClient.get<PageResponse<InventoryTransactionResponse>>(
            "/inventory/inventory-transactions/store",
            { query }
        );
    },
    detail(id: EntityId) {
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
    },
    storeStockIn(input: StoreStockInRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StoreStockInRequest
        >("/inventory/inventory-transactions/store/stock-in", input);
    },
    storeAdjust(input: StoreStockAdjustmentRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StoreStockAdjustmentRequest
        >("/inventory/inventory-transactions/store/adjustment", input);
    },
    storeStockOut(input: StoreStockOutRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StoreStockOutRequest
        >("/inventory/inventory-transactions/store/stock-out", input);
    },
    storeTransfer(input: StoreTransferRequest) {
        return apiClient.post<
            InventoryTransactionResponse,
            StoreTransferRequest
        >("/inventory/inventory-transactions/store/transfer", input);
    }
};
