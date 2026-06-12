import { QueryParams } from "../core/query-params";
import type { ApiClient } from "../core/types";
import type { PageResponse } from "./product.api";

export interface InventoryItem {
    id: string;
    branchId: string;
    productVariantId: string;
    sku: string;
    productName: string;
    quantityOnHand: number;
    quantityReserved: number;
    quantityAvailable: number;
    reorderLevel?: number;
    updatedAt: string;
}

export interface InventoryQuery extends QueryParams {
    page?: number;
    size?: number;
    branchId?: string;
    keyword?: string;
    lowStock?: boolean;
}

export interface AdjustInventoryInput {
    branchId: string;
    productVariantId: string;
    quantity: number;
    reason: string;
    referenceCode?: string;
}

export function createInventoryApi(
    apiClient: ApiClient
) {
    return {
        getInventory(query: InventoryQuery = {}) {
            return apiClient.get<
                PageResponse<InventoryItem>
            >("/inventory", {
                query
            });
        },

        getInventoryByVariant(
            branchId: string,
            productVariantId: string
        ) {
            return apiClient.get<InventoryItem>(
                `/inventory/branches/${encodeURIComponent(
                    branchId
                )}/variants/${encodeURIComponent(
                    productVariantId
                )}`
            );
        },

        adjustInventory(input: AdjustInventoryInput) {
            return apiClient.post<
                InventoryItem,
                AdjustInventoryInput
            >("/inventory/adjustments", input);
        },

        exportInventory(query: InventoryQuery = {}) {
            return apiClient.download(
                "/inventory/export",
                {
                    query
                }
            );
        }
    };
}

export type InventoryApi =
    ReturnType<typeof createInventoryApi>;