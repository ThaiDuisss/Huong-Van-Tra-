import type {
    InventoryItemType,
    InventoryLocationType
} from "./inventory.types";

export interface ReplenishmentPolicy {
    id: string;
    inventoryItemId: string;
    itemCode: string;
    itemName: string;
    itemType: InventoryItemType;
    locationType: InventoryLocationType;
    minimumStock: number;
    maximumStock: number | null;
    enabled: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface ReplenishmentPolicyCell {
    id: string;
    minimumStock: number;
    maximumStock: number | null;
    enabled: boolean;
}

export interface ReplenishmentPolicyMatrixRow {
    inventoryItemId: string;
    itemCode: string;
    itemName: string;
    itemType: InventoryItemType;
    warehousePolicy: ReplenishmentPolicyCell | null;
    shelfPolicy: ReplenishmentPolicyCell | null;
}

export interface ReplenishmentPolicyQuery {
    [key: string]: string | number | boolean | undefined;
    inventoryItemId?: string;
    locationType?: InventoryLocationType;
    itemType?: InventoryItemType;
    enabled?: boolean;
    search?: string;
    page?: number;
    size?: number;
}

export interface ReplenishmentPolicyMatrixQuery {
    [key: string]: string | number | boolean | undefined;
    itemType?: InventoryItemType;
    search?: string;
    page?: number;
    size?: number;
}

export interface ReplenishmentPolicyRequest {
    inventoryItemId: string;
    locationType: InventoryLocationType;
    minimumStock: number;
    maximumStock: number | null;
    enabled: boolean;
}
