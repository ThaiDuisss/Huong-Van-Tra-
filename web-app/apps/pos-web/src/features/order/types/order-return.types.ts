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

export type CustomerReturnQcResult = "RESTOCK" | "DAMAGED" | "QUARANTINE" | "WRITE_OFF";

export type CustomerReturnQcStatus =
    | "PENDING"
    | "PARTIAL"
    | "COMPLETED"
    | "UNAVAILABLE"
    | "UNKNOWN";

export type CustomerReturnInspectionStatus = "CREATED" | "POSTED" | "CANCELLED";

export type OrderRefundStatus = "PENDING" | "REFUNDED" | "NOT_READY" | "FAILED" | "NOT_REQUIRED";

export type OrderRefundBlockReason =
    | "NONE"
    | "RETURN_NOT_RECEIVED"
    | "PHYSICAL_DISPOSITION_NOT_COMPLETED"
    | "REFUND_NOT_REQUIRED"
    | "ALREADY_REFUNDED"
    | "REFUND_FAILED"
    | "PAYMENT_SERVICE_UNAVAILABLE"
    | "PAYMENT_NOT_FOUND"
    | "PAYMENT_NOT_REFUNDABLE"
    | "UNSUPPORTED_PAYMENT_METHOD"
    | "NO_REFUND_AMOUNT"
    | "REFUND_AMOUNT_EXCEEDS_REMAINING";

export type OrderReturnStatus = "NONE" | "PARTIALLY_RETURNED" | "FULLY_RETURNED";

export type ReturnDisplayStatus =
    | "CREATED"
    | "RECEIVED"
    | "PROCESSING"
    | "PROCESSING_FAILED"
    | "PENDING_REFUND"
    | "COMPLETED"
    | "CANCELLED";

export type ReturnFilterKey = "ALL" | ReturnDisplayStatus;

export type OrderReturnStatusCounts = Record<ReturnFilterKey, number>;

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
    refundRequired?: boolean;
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
    refundRequired?: boolean | null;
    branchId?: string | null;
    orderCode?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    note?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    items?: OrderReturnItemResponse[] | null;
    displayStatus?: ReturnDisplayStatus | null;
    physicalDone?: boolean | null;
    requiredQuantity?: number | string | null;
    processedQuantity?: number | string | null;
    remainingQuantity?: number | string | null;
    inventoryProcessingStatus?: string | null;
}

export interface OrderReturnSummaryResponse {
    id: string;
    returnCode?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    branchId?: string | null;
    status?: ReturnStatus | null;
    refundRequired?: boolean | null;
    createdAt?: string | null;
    updatedAt?: string | null;
    itemCount?: number | string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    qcStatus?: CustomerReturnQcStatus | string | null;
    remainingQcQuantity?: number | string | null;
    inspectionStatus?: CustomerReturnInspectionStatus | string | null;
    refundStatus?: OrderRefundStatus | null;
    hasFailedDisposition?: boolean | null;
    isPendingDisposition?: boolean | null;
    postedQcQuantity?: number | string | null;
    displayStatus?: ReturnDisplayStatus | null;
    physicalDone?: boolean | null;
    requiredQuantity?: number | string | null;
    processedQuantity?: number | string | null;
    remainingQuantity?: number | string | null;
    inventoryProcessingStatus?: string | null;
}

export interface OrderReturnListRow extends OrderReturnSummaryResponse {
    note?: string | null;
    items?: OrderReturnItemResponse[] | null;
}

export interface OrderReturnQueryParams {
    fromDate?: string;
    keyword?: string;
    status?: ReturnStatus | string;
    displayStatus?: string;
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
    displayStatus?: ReturnDisplayStatus | string | null;
    refundRequired?: boolean | null;
    qcStatus?: CustomerReturnQcStatus | null;
    physicalDone?: boolean | null;
    inventoryProcessingStatus?: string | null;
    inspectionId?: string | null;
    inventoryProcessedAt?: string | null;
    refundStatus?: OrderRefundStatus | null;
    refundEligible?: boolean | null;
    refundBlockReason?: OrderRefundBlockReason | string | null;
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
    blockReason?: OrderRefundBlockReason | string | null;
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
    dispositions?: CustomerReturnQcDispositionSummary[] | null;
}

export interface CustomerReturnQcDispositionSummary {
    result: CustomerReturnQcResult;
    quantity?: number | string | null;
    destinationLocationId?: string | null;
    destinationLocationName?: string | null;
    transactionId?: string | null;
    transactionType?: "CUSTOMER_RETURN_RECEIPT" | "WRITE_OFF" | string | null;
    processingStatus?: string | null;
    processedAt?: string | null;
}

export interface OrderReturnTimelineResponse {
    id: string;
    action: "CREATED" | "RECEIVED" | "CANCELLED" | "QC_COMPLETED" | "REFUND_COMPLETED" | "COMPLETED";
    fromStatus?: ReturnStatus | null;
    toStatus?: ReturnStatus | null;
    actorUserId?: string | null;
    note?: string | null;
    referenceType?: string | null;
    referenceId?: string | null;
    occurredAt?: string | null;
}

export interface CustomerReturnQcSummaryResponse {
    returnId: string;
    returnCode?: string | null;
    returnStatus?: ReturnStatus | null;
    branchId?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    inspectionId?: string | null;
    inspectionStatus?: CustomerReturnInspectionStatus | null;
    qcStatus?: CustomerReturnQcStatus | null;
    lines?: CustomerReturnQcSummaryLine[] | null;
    inspectedAt?: string | null;
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

export type OrderReturnInventoryCommandStatus =
    | "PENDING"
    | "PROCESSED"
    | "FAILED";

export interface SubmitReturnInventoryDispositionLinePayload {
    returnItemId: string;
    quantity: number;
    result: CustomerReturnQcResult;
    destinationLocationId?: string | null;
    note?: string | null;
}

export interface SubmitReturnInventoryDispositionPayload {
    commandId: string;
    lines: SubmitReturnInventoryDispositionLinePayload[];
}

export interface OrderReturnInventoryDispositionLineResponse {
    returnItemId?: string | null;
    orderItemId?: string | null;
    productVariantId?: string | null;
    quantity?: number | string | null;
    result?: CustomerReturnQcResult | null;
    destinationLocationId?: string | null;
    note?: string | null;
}

export interface OrderReturnInventoryDispositionResponse {
    commandId: string;
    returnId: string;
    returnCode?: string | null;
    status: OrderReturnInventoryCommandStatus;
    createdAt?: string | null;
    processedAt?: string | null;
    failedAt?: string | null;
    failureCode?: string | null;
    failureReason?: string | null;
    inspectionId?: string | null;
    lines?: OrderReturnInventoryDispositionLineResponse[] | null;
}
