export type SalesHistoryStatusFilter =
    | "ALL"
    | "PENDING_PAYMENT"
    | "COMPLETED"
    | "CANCELLED";

export type SalesHistoryPaymentFilter =
    | "ALL"
    | "CASH"
    | "BANK_TRANSFER"
    | "DEBT";

export interface SalesHistoryFilters {
    status: SalesHistoryStatusFilter;
    paymentMethod: SalesHistoryPaymentFilter;
}

export interface SalesHistorySummary {
    todayRevenue: number | string | null;
    totalOrders: number;
    completedOrders: number;
    pendingPaymentOrders: number;
    averageOrderValue: number | string | null;
    pendingAmount: number | string | null;
}

export type SalesOrderStatus =
    | "DRAFT"
    | "PENDING_PAYMENT"
    | "PAYMENT_CONFIRMED"
    | "COMPLETED"
    | "CANCELLED"
    | "EXPIRED"
    | "PAYMENT_FAILED"
    | string;

export type SalesPaymentStatus =
    | "PAID"
    | "UNPAID"
    | "WAITING_PAYMENT"
    | "PENDING"
    | "PENDING_PAYMENT"
    | "FAILED"
    | "REFUNDED"
    | "PARTIALLY_REFUNDED"
    | string;

export type SalesPaymentMethod =
    | "CASH"
    | "BANK_TRANSFER"
    | "TRANSFER"
    | "QR_CODE"
    | "DEBT"
    | string;

export interface SalesHistoryPageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}

export interface SalesHistoryOrder {
    orderId: string;
    orderCode: string;
    soldAt?: string | null;
    createdAt?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    cashierName?: string | null;
    totalItems?: number | string | null;
    finalAmount?: number | string | null;
    paymentMethod?: SalesPaymentMethod | null;
    paymentMethodLabel?: string | null;
    status?: SalesOrderStatus | null;
    statusLabel?: string | null;
    paymentStatus?: SalesPaymentStatus | null;
    paymentStatusLabel?: string | null;
}

export interface SalesOrderItemDetail {
    productName?: string | null;
    productNameSnapshot?: string | null;
    variantName?: string | null;
    variantNameSnapshot?: string | null;
    name?: string | null;
    sku?: string | null;
    skuSnapshot?: string | null;
    quantity?: number | string | null;
    unitPrice?: number | string | null;
    actualUnitPrice?: number | string | null;
    price?: number | string | null;
    lineTotal?: number | string | null;
}

export interface SalesOrderDetail {
    id?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    soldAt?: string | null;
    createdAt?: string | null;
    cashierName?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    paymentMethod?: SalesPaymentMethod | null;
    paymentMethodLabel?: string | null;
    status?: SalesOrderStatus | null;
    statusLabel?: string | null;
    paymentStatus?: SalesPaymentStatus | null;
    paymentStatusLabel?: string | null;
    subTotal?: number | string | null;
    originalAmount?: number | string | null;
    discountAmount?: number | string | null;
    finalAmount?: number | string | null;
    paidAmount?: number | string | null;
    changeAmount?: number | string | null;
    debtAmount?: number | string | null;
    items?: SalesOrderItemDetail[] | null;
}
