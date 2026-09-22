import type {
    CustomerReturnQcResult,
    CustomerReturnQcStatus,
    ReturnReason,
    ReturnStatus
} from "./order-return.types";

export type ReturnReportRefundStatus = "REFUNDED" | "NOT_REFUNDED";

export interface OrderReturnStatusBreakdown {
    status: ReturnStatus;
    count: number;
}

export interface OrderReturnReasonBreakdown {
    reason: ReturnReason;
    itemCount: number;
    quantity: number | string;
}

export interface OrderReturnQcBreakdown {
    result: CustomerReturnQcResult;
    quantity: number | string;
    percentage: number | string;
}

export interface OrderReturnReportSummary {
    totalReturns: number;
    completedReturns: number;
    processingReturns: number;
    cancelledReturns: number;
    ordersWithReturns: number;
    returnedQuantity: number | string;
    refundedReturnCount: number;
    totalRefundAmount: number | string;
    averageRefundAmount: number | string;
    statusBreakdown: OrderReturnStatusBreakdown[];
    reasonBreakdown: OrderReturnReasonBreakdown[];
    qcBreakdown: OrderReturnQcBreakdown[];
}

export interface OrderReturnReportDetail {
    returnId: string;
    returnCode?: string | null;
    orderId?: string | null;
    orderCode?: string | null;
    branchId?: string | null;
    customerName?: string | null;
    customerPhone?: string | null;
    returnStatus?: ReturnStatus | null;
    qcStatus?: CustomerReturnQcStatus | null;
    refundAmount?: number | string | null;
    method?: string | null;
    paymentStatus?: string | null;
    refundStatus?: ReturnReportRefundStatus | null;
    returnCreatedAt?: string | null;
    refundedAt?: string | null;
}

export interface OrderReturnReportQueryParams {
    keyword?: string;
    returnStatus?: ReturnStatus;
    refundStatus?: ReturnReportRefundStatus;
    branchId?: string;
    fromDate?: string;
    toDate?: string;
    page?: number;
    size?: number;
    sort?: string;
}
