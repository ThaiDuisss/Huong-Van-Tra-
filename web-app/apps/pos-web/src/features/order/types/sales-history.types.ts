export type SalesHistoryStatusFilter =
    | "ALL"
    | "UNPAID"
    | "PAID"
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
    totalOrders: number;
    completedOrders: number;
    pendingPaymentOrders: number;
    cancelledOrders: number;
    grossAmount: number;
    discountAmount: number;
    netRevenue: number;
    averageOrderValue: number;
    totalItemsSold: number;
    receivableAmount: number;
}

export type SalesOrderStatus =
    | "COMPLETED"
    | "PENDING_PAYMENT"
    | "PROCESSING"
    | "SHIPPING"
    | "CANCELLED";

export type SalesPaymentStatus =
    | "PAID"
    | "UNPAID"
    | "PENDING"
    | "PENDING_PAYMENT"
    | "CANCELLED";

export type SalesPaymentMethod = "CASH" | "BANK_TRANSFER" | "TRANSFER" | "DEBT";

export interface SalesHistoryFilterCounts {
    status: Record<SalesHistoryStatusFilter, number>;
    paymentMethod: Record<SalesHistoryPaymentFilter, number>;
}

export interface SalesOrderItem {
    name: string;
    sku: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
}

export interface SalesHistoryOrder {
    orderId: string;
    orderCode: string;
    createdAt: string;
    customerName: string;
    customerPhone: string | null;
    cashierName: string;
    orderStatus: SalesOrderStatus;
    paymentStatus: SalesPaymentStatus;
    paymentMethod: SalesPaymentMethod;
    totalItems: number;
    totalAmount: number;
    discountAmount: number;
    finalAmount: number;
    paidAmount: number;
    changeAmount: number;
    debtAmount: number;
    items: SalesOrderItem[];
}

export interface SalesHistoryResponse {
    summary: SalesHistorySummary;
    filterCounts: SalesHistoryFilterCounts;
    orders: SalesHistoryOrder[];
}
