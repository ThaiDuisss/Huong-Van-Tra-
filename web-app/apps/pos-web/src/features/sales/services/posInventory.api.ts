import { apiClient } from "../../../api/api-client";

export interface PosInventorySearchParams {
    sku?: string;
    productName?: string;
}

export interface PosInventoryBalance {
    id: string;
    locationId: string;
    inventoryItemId: string;
    variantId?: string | null;
    sku?: string | null;
    itemType: string;
    quantityOnHand: number;
    quantityReserved: number;
    availableQuantity: number;
    status: string;
}

export const posInventoryApi = {
    searchInventoryForPos(params: PosInventorySearchParams) {
        return apiClient.get<PosInventoryBalance[]>(
            "/inventory/inventory-balances/search-view-pos",
            {
                query: {
                    sku: params.sku,
                    productName: params.productName
                }
            }
        );
    }
};
