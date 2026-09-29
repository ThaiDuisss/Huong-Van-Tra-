import { apiClient } from "../../../api/api-client";

export interface WarehouseInventorySummary {
    inventoryItemId: string;
    productCode: string;
    productName: string;
    categoryName: string;
    unitName: string;
    totalQuantity: number;
    shelfQuantity: number; // POS SHELF
    warehouseQuantity: number; // WAREHOUSE
    reorderPoint: number;
    stockStatus: "OUT_OF_STOCK" | "LOW_STOCK" | "SUFFICIENT";
}

export interface LocationInventoryDetail {
    locationId: string;
    locationName: string;
    locationType: string; // SHELF, WAREHOUSE
    quantityOnHand: number;
    quantityReserved: number;
    quantityBlocked: number;
    availableQuantity: number;
    batchCount: number;
}

export const warehouseInventoryApi = {
    /**
     * Lấy danh sách sản phẩm với tồn kho (tách SHELF vs WAREHOUSE)
     */
    getProductInventorySummary(branchId: string, params?: { keyword?: string; page?: number; size?: number }) {
        return apiClient.get<{
            content: WarehouseInventorySummary[];
            page: number;
            size: number;
            totalElements: number;
            totalPages: number;
        }>(`/inventory/warehouse/products`, {
            query: { branchId, ...params }
        });
    },

    /**
     * Lấy chi tiết tồn kho của sản phẩm theo từng location
     */
    getProductLocationInventory(itemId: string) {
        return apiClient.get<LocationInventoryDetail[]>(
            `/inventory/warehouse/products/${itemId}/locations`
        );
    }
};
