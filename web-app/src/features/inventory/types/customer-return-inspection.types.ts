export type CustomerReturnInspectionStatus = "CREATED" | "PROCESSING" | "POSTED" | "CANCELLED";

export type CustomerReturnQcResult = "RESTOCK" | "DAMAGED" | "QUARANTINE" | "WRITE_OFF";

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
    transactionType?: "CUSTOMER_RETURN_RECEIPT" | "WRITE_OFF" | string;
    disposalRequestId?: string;
    disposalRequestCode?: string;
    disposalStatus?: "PENDING_APPROVAL" | "APPROVED";
    condition?: string;
    returnReason?: string;
}

export interface CustomerReturnInspection {
    id: string;
    inspectionCode: string;
    returnId: string;
    returnCode: string;
    branchId?: string | null;
    orderId?: string;
    orderCode?: string;
    customerName?: string;
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
    fromDate?: string;
    toDate?: string;
}

export interface CustomerReturnInspectionListResult {
    content: CustomerReturnInspection[];
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
