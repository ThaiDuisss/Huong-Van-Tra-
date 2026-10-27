import { apiClient } from "../../../api/api-client";

export interface WarehouseBatchResponse {
    batchId: string;
    batchCode: string;
    supplierBatchCode?: string;
    manufactureDate?: string;
    expiryDate?: string;
    status: string;
    unitSymbol: string;
    quantityOnHand: number;
    quantityReserved: number;
    quantityBlocked: number;
    availableQuantity: number;
    daysUntilExpiry?: number;
    itemType?: string;
}

export const warehouseBatchApi = {
    /**
     * Lấy danh sách batch của item tại location, sắp xếp theo FEFO
     */
    getBatchesByItemAndLocation(itemId: string, locationId: string) {
        return apiClient.get<WarehouseBatchResponse[]>(
            `/inventory/batches`,
            { query: { itemId, locationId } }
        );
    },

    /**
     * Lấy chi tiết batch tại location
     */
    getBatchDetail(batchId: string, locationId: string) {
        return apiClient.get<WarehouseBatchResponse>(
            `/inventory/batches/${batchId}`,
            { query: { locationId } }
        );
    }
};
