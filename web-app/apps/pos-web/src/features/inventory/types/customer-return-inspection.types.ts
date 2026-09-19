export type CustomerReturnInspectionStatus = "CREATED" | "POSTED" | "CANCELLED";

export type CustomerReturnQcResult = "RESTOCK" | "DAMAGED" | "WRITE_OFF";

export interface CustomerReturnInspectionLine {
    id: string;
    returnItemId: string;
    orderItemId?: string;
    productVariantId: string;
    inventoryItemId: string;
    itemCode?: string;
    itemName?: string;
    quantity: number;
    result: CustomerReturnQcResult;
    destinationLocationId?: string;
    destinationLocationName?: string;
    note?: string;
    posted: boolean;
    transactionId?: string;
}

export interface CustomerReturnInspection {
    id: string;
    inspectionCode: string;
    returnId: string;
    returnCode: string;
    branchId: string;
    orderId?: string;
    orderCode?: string;
    status: CustomerReturnInspectionStatus;
    createdAt: string;
    postedAt?: string;
    note?: string;
    lines: CustomerReturnInspectionLine[];
}

export interface CustomerReturnInspectionListQuery {
    page: number;
    size: number;
    returnCode: string;
    orderCode: string;
    status: "ALL" | CustomerReturnInspectionStatus;
}

export interface CustomerReturnInspectionListResult {
    content: CustomerReturnInspection[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
