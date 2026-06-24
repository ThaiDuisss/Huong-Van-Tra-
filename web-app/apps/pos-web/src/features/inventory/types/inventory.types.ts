import type { QueryParams } from "@hvt/api-client";

export type EntityId = string | number;

export type InventoryItemType =
    | "PRODUCT_VARIANT"
    | "INGREDIENT"
    | "PACKAGING"
    | "COMBO";

export type InventoryLocationType =
    | "WAREHOUSE"
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
    id: EntityId;
    branchId: EntityId;
    parentLocationId: EntityId | null;
    code: string;
    name: string;
    type: InventoryLocationType;
    status: InventoryStatus;
}

export interface InventoryLocationRequest {
    branchId: EntityId;
    parentLocationId: EntityId | null;
    code: string;
    name: string;
    type: InventoryLocationType;
    status: InventoryStatus;
}

export interface InventoryItemResponse {
    id: EntityId;
    referenceId: EntityId;
    itemType: InventoryItemType;
    baseUnitId: EntityId;
    status: InventoryStatus;
}

export interface InventoryBalanceResponse {
    id: EntityId;
    inventoryItemId: EntityId;
    locationId: EntityId;
    branchId: EntityId;
    quantityOnHand: number;
    quantityReserved: number;
    availableQuantity: number;
    version: number;
    updatedAt: string;
}

export interface InventoryTransactionLineResponse {
    id: EntityId;
    inventoryItemId: EntityId;
    locationId: EntityId;
    quantity: number;
    beforeQuantity: number;
    afterQuantity: number;
}

export interface InventoryTransactionResponse {
    id: EntityId;
    transactionCode: string;
    type: InventoryTransactionType;
    referenceType: InventoryReferenceType;
    referenceId: EntityId | null;
    branchId: EntityId;
    createdBy: EntityId;
    createdAt: string;
    lines: InventoryTransactionLineResponse[];
}

export interface StockInRequest {
    branchId: EntityId;
    referenceType: InventoryReferenceType;
    referenceId: EntityId | null;
    lines: Array<{
        inventoryItemId: EntityId;
        locationId: EntityId;
        quantity: number;
    }>;
}

export interface StockAdjustmentRequest {
    branchId: EntityId;
    referenceType: InventoryReferenceType;
    referenceId: EntityId | null;
    lines: Array<{
        inventoryItemId: EntityId;
        locationId: EntityId;
        actualQuantity: number;
    }>;
}

export interface InventoryLocationQuery extends QueryParams {
    page?: number;
    size?: number;
    branchId?: EntityId;
    type?: InventoryLocationType;
    status?: InventoryStatus;
}

export interface InventoryItemQuery extends QueryParams {
    page?: number;
    size?: number;
    referenceId?: EntityId;
    itemType?: InventoryItemType;
    status?: InventoryStatus;
}

export interface InventoryBalanceQuery extends QueryParams {
    page?: number;
    size?: number;
    inventoryItemId?: EntityId;
    locationId?: EntityId;
    branchId?: EntityId;
    sku?: string;
    productCode?: string;
    productName?: string;
    categoryCode?: string;
    categoryId?: string;
    unitId?: string;
}

export interface InventoryBalanceViewResponse {
    inventoryBalanceId: string;
    inventoryItemId: string;
    productId: string;
    productCode: string;
    productName: string;
    variantId: string;
    variantName: string;
    sku: string;
    categoryId: string;
    categoryCode: string;
    categoryName: string;
    unitId: string;
    unitName: string;
    unitSymbol: string;
    locationId: string;
    locationName: string;
    branchId: string;
    imageUrl: string | null;
    quantityOnHand: number;
    quantityReserved: number;
    availableQuantity: number;
    cost_price?: number;
    selling_price?: number;
    updatedAt: string;
}

export interface InventoryTransactionQuery extends QueryParams {
    page?: number;
    size?: number;
    transactionCode?: string;
    type?: InventoryTransactionType;
    fromDate?: string;
    toDate?: string;
    createdBy?: EntityId;
    branchId?: EntityId;
    referenceType?: InventoryReferenceType;
    referenceId?: EntityId;
}
