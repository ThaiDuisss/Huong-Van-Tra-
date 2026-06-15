import type { QueryParams } from "@hvt/api-client";

export type InventoryItemType =
    | "PRODUCT_VARIANT"
    | "INGREDIENT"
    | "PACKAGING"
    | "COMBO";

export type InventoryLocationType =
    | "SHELF"
    | "COUNTER"
    | "STORAGE"
    | "DAMAGED";

export type InventoryStatus = "ACTIVE" | "INACTIVE";

export type InventoryTransactionType =
    | "STOCK_IN"
    | "SALE_OUT"
    | "RETURN_IN"
    | "ADJUSTMENT"
    | "TRANSFER";

export type InventoryReferenceType =
    | "MANUAL"
    | "ORDER"
    | "RETURN_ORDER"
    | "STOCK_TRANSFER"
    | "STOCK_COUNT";

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
}

export interface InventoryLocationResponse {
    id: number;
    branchId: number;
    parentLocationId: number | null;
    code: string;
    name: string;
    type: InventoryLocationType;
    status: InventoryStatus;
}

export interface InventoryLocationRequest {
    branchId: number;
    parentLocationId: number | null;
    code: string;
    name: string;
    type: InventoryLocationType;
    status: InventoryStatus;
}

export interface InventoryItemResponse {
    id: number;
    referenceId: number;
    itemType: InventoryItemType;
    baseUnitId: number;
    status: InventoryStatus;
}

export interface InventoryBalanceResponse {
    id: number;
    inventoryItemId: number;
    locationId: number;
    branchId: number;
    quantityOnHand: number;
    quantityReserved: number;
    availableQuantity: number;
    version: number;
    updatedAt: string;
}

export interface InventoryTransactionLineResponse {
    id: number;
    inventoryItemId: number;
    locationId: number;
    quantity: number;
    beforeQuantity: number;
    afterQuantity: number;
}

export interface InventoryTransactionResponse {
    id: number;
    transactionCode: string;
    type: InventoryTransactionType;
    referenceType: InventoryReferenceType;
    referenceId: number | null;
    branchId: number;
    createdBy: number;
    createdAt: string;
    lines: InventoryTransactionLineResponse[];
}

export interface StockInRequest {
    branchId: number;
    referenceType: InventoryReferenceType;
    referenceId: number | null;
    lines: Array<{
        inventoryItemId: number;
        locationId: number;
        quantity: number;
    }>;
}

export interface StockAdjustmentRequest {
    branchId: number;
    referenceType: InventoryReferenceType;
    referenceId: number | null;
    lines: Array<{
        inventoryItemId: number;
        locationId: number;
        actualQuantity: number;
    }>;
}

export interface InventoryLocationQuery extends QueryParams {
    page?: number;
    size?: number;
    branchId?: number;
    type?: InventoryLocationType;
    status?: InventoryStatus;
}

export interface InventoryItemQuery extends QueryParams {
    page?: number;
    size?: number;
    referenceId?: number;
    itemType?: InventoryItemType;
    status?: InventoryStatus;
}

export interface InventoryBalanceQuery extends QueryParams {
    page?: number;
    size?: number;
    inventoryItemId?: number;
    locationId?: number;
    branchId?: number;
}

export interface InventoryTransactionQuery extends QueryParams {
    page?: number;
    size?: number;
    transactionCode?: string;
    type?: InventoryTransactionType;
    fromDate?: string;
    toDate?: string;
    createdBy?: number;
    branchId?: number;
    referenceType?: InventoryReferenceType;
    referenceId?: number;
}
