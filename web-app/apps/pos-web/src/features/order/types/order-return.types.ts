export type ReturnStatus = "CREATED" | "RECEIVED" | "COMPLETED" | "CANCELLED";

export type ReturnReason =
    | "DEFECTIVE"
    | "DAMAGED"
    | "WRONG_ITEM"
    | "QUALITY_ISSUE"
    | "EXPIRED"
    | "CHANGE_OF_MIND"
    | "OTHER";

export type ReturnCondition =
    | "SEALED"
    | "OPENED"
    | "DAMAGED"
    | "SPOILED"
    | "USED";

export type CustomerReturnQcResult = "RESTOCK" | "QUARANTINE" | "WRITE_OFF";

export type CustomerReturnQcStatus = "PENDING" | "PARTIAL" | "COMPLETED";

export type CustomerReturnInspectionStatus = "CREATED" | "POSTED" | "CANCELLED";

export type OrderRefundStatus = "PENDING" | "REFUNDED" | "NOT_READY" | "FAILED";

export type OrderReturnStatus = "NONE" | "PARTIALLY_RETURNED" | "FULLY_RETURNED";

export interface CreateOrderReturnItemPayload {
    orderItemId: string;
    quantity: number;
    reason: ReturnReason;
    condition: ReturnCondition;
    note?: string | null;
}

export interface CreateOrderReturnPayload {
    items: CreateOrderReturnItemPayload[];
    note?: string | null;
}

export interface OrderReturnItemResponse {
    id?: string | null;
    orderItemId?: string | null;
    productVariantId?: string | null;
    quantity?: number | string | null;
    reason?: ReturnReason | null;
    condition?: ReturnCondition | null;
    note?: string | null;
    skuSnapshot?: string | null;
    productNameSnapshot?: string | null;
    variantNameSnapshot?: string | null;
}

export interface OrderReturnResponse {
    id: string;
    returnCode?: string | null;
    orderId?: string | null;
    status?: ReturnStatus | null;
    branchId?: string | null;
    orderCode?: string | null;
    note?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnSummaryResponse {
    id: string;
    returnCode?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    branchId?: string | null;
    status?: ReturnStatus | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    itemCount?: number | string | null;
    customerName?: string | null;
    customerPhone?: string | null;
}

export interface OrderReturnListRow extends OrderReturnSummaryResponse {
    note?: string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnQueryParams {
    fromDate?: string;
    keyword?: string;
    status?: ReturnStatus;
    toDate?: string;
    page?: number;
    size?: number;
    sort?: string;
}

export interface OrderRefundQueryParams {
    fromDate?: string;
    keyword?: string;
    status?: OrderRefundStatus;
    toDate?: string;
    page?: number;
    size?: number;
    sort?: string;
}

export interface OrderRefundSummaryResponse {
    returnId: string;
    returnCode?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    returnCreatedAt?: string | null;
    returnStatus?: ReturnStatus | null;
    qcStatus?: CustomerReturnQcStatus | null;
    refundStatus?: OrderRefundStatus | null;
    refundAmount?: number | string | null;
    paymentStatus?: string | null;
    method?: string | null;
    refundedAt?: string | null;
}

export interface OrderRefundDetailResponse extends OrderRefundSummaryResponse {
    totalAmountRefunded?: number | string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnRefundPreviewResponse {
    returnId: string;
    orderId?: string | null;
    refundAmount?: number | string | null;
    paymentMethod?: string | null;
    eligible?: boolean;
    reason?: string | null;
}

export interface OrderReturnRefundResponse {
    returnId: string;
    orderId?: string | null;
    paymentId?: string | null;
    refundTransactionId?: string | null;
    refundAmount?: number | string | null;
    totalAmountRefunded?: number | string | null;
    paymentStatus?: string | null;
    returnStatus?: ReturnStatus | null;
    orderPaymentStatus?: string | null;
    orderReturnStatus?: OrderReturnStatus | null;
    method?: string | null;
    refundedAt?: string | null;
}

export interface OrderRefundStatsResponse {
    total?: number;
    pendingRefund?: number;
    refunded?: number;
    notReady?: number;
    failed?: number;
}

export interface CustomerReturnQcSummaryLine {
    returnItemId: string;
    orderItemId?: string | null;
    productVariantId?: string | null;
    returnedQuantity?: number | string | null;
    postedQcQuantity?: number | string | null;
    remainingQcQuantity?: number | string | null;
    status?: CustomerReturnQcStatus | null;
    condition?: ReturnCondition | null;
    reason?: ReturnReason | string | null;
}

export interface CustomerReturnQcSummaryResponse {
    returnId: string;
    returnCode?: string | null;
    returnStatus?: ReturnStatus | null;
    branchId?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    qcStatus?: CustomerReturnQcStatus | null;
    lines?: CustomerReturnQcSummaryLine[] | null;
}

export interface CreateCustomerReturnInspectionLinePayload {
    returnItemId: string;
    quantity: number;
    result: CustomerReturnQcResult;
    destinationLocationId?: string | null;
    note?: string | null;
}

export interface CreateCustomerReturnInspectionPayload {
    note?: string | null;
    lines: CreateCustomerReturnInspectionLinePayload[];
}

export interface CustomerReturnInspectionLine {
    id: string;
    returnItemId: string;
    orderItemId?: string | null;
    productVariantId?: string | null;
    inventoryItemId?: string | null;
    quantity?: number | string | null;
    result?: CustomerReturnQcResult | null;
    destinationLocationId?: string | null;
    note?: string | null;
    posted?: boolean | null;
    transactionId?: string | null;
}

export interface CustomerReturnInspectionResponse {
    id: string;
    inspectionCode?: string | null;
    returnId?: string | null;
    returnCode?: string | null;
    branchId?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    status?: CustomerReturnInspectionStatus | null;
    postedAt?: string | null;
    note?: string | null;
    lines?: CustomerReturnInspectionLine[] | null;
}
