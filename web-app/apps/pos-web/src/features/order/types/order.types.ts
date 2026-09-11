export interface OrderPageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface OrderQueryParams {
    keyword?: string;
    status?: string;
    paymentStatus?: string;
    orderType?: string;
    fromDate?: string;
    toDate?: string;
    returnEligibility?: OrderReturnEligibility;
    sort?: "businessStatus" | "invoicePayment";
    page?: number;
    size?: number;
}

export type OrderReturnEligibility = "ALL" | "ELIGIBLE" | "INELIGIBLE";

export interface OrderSummaryResponse {
    id: string;
    orderCode?: string | null;
    invoiceCode?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    orderType?: string | null;
    orderTypeLabel?: string | null;
    status?: string | null;
    statusLabel?: string | null;
    paymentStatus?: string | null;
    paymentStatusLabel?: string | null;
    finalAmount?: number | string | null;
    createdAt?: string | null;
    createdBy?: string | null;
    createdByName?: string | null;
    cashierId?: string | null;
    cashierName?: string | null;
}

export interface OrderItemResponse {
    id?: string | null;
    productVariantId?: string | null;
    skuSnapshot?: string | null;
    productNameSnapshot?: string | null;
    variantNameSnapshot?: string | null;
    quantity?: number | string | null;
    originalUnitPrice?: number | string | null;
    actualUnitPrice?: number | string | null;
    discountAmount?: number | string | null;
    lineTotal?: number | string | null;
    bomVersionId?: string | null;
    unitNameSnapshot?: string | null;
    imageUrlSnapshot?: string | null;
    taxAmount?: number | string | null;
    note?: string | null;
}

export interface OrderStatusHistoryResponse {
    id?: string | null;
    oldStatus?: string | null;
    newStatus?: string | null;
    fromStatus?: string | null;
    toStatus?: string | null;
    status?: string | null;
    oldStatusLabel?: string | null;
    newStatusLabel?: string | null;
    fromStatusLabel?: string | null;
    toStatusLabel?: string | null;
    statusLabel?: string | null;
    note?: string | null;
    createdBy?: string | null;
    createdByName?: string | null;
    createdAt?: string | null;
}

export interface OrderDetailResponse extends OrderSummaryResponse {
    tenantId?: string | null;
    branchId?: string | null;
    branchName?: string | null;
    terminalId?: string | null;
    terminalName?: string | null;
    shiftId?: string | null;
    shiftCode?: string | null;
    customerId?: string | null;
    customerEmail?: string | null;
    recipientName?: string | null;
    recipientPhone?: string | null;
    shippingAddress?: string | null;
    shippingProvider?: string | null;
    originalAmount?: number | string | null;
    discountAmount?: number | string | null;
    shippingFee?: number | string | null;
    paidAmount?: number | string | null;
    debtAmount?: number | string | null;
    note?: string | null;
    updatedBy?: string | null;
    updatedAt?: string | null;
    paymentMethod?: string | null;
    paymentMethodName?: string | null;
    items?: OrderItemResponse[] | null;
    statusHistories?: OrderStatusHistoryResponse[] | null;
}

export interface OrderStatsResponse {
    total?: number | null;
    pending?: number | null;
    processing?: number | null;
    shipping?: number | null;
    completed?: number | null;
    returnedOrRefunded?: number | null;
    unpaidInvoices?: number | null;
    paidInvoices?: number | null;
}

export interface OrderReturnEligibilityStatsResponse {
    total?: number | null;
    eligible?: number | null;
    ineligible?: number | null;
}

export interface UpdateOrderStatusRequest {
    status: string;
    note?: string;
}

export interface CancelOrderRequest {
    reason: string;
}
