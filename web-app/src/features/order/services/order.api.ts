import { apiClient } from "../../../api/api-client";
import type {
    CancelOrderRequest,
    OrderDetailResponse,
    OrderPageResponse,
    OrderQueryParams,
    OrderReturnEligibilityStatsResponse,
    OrderStatsResponse,
    OrderSummaryResponse,
    UpdateOrderStatusRequest
} from "../types/order.types";
import type { OrderImportRequest, OrderImportResponse } from "../types/order-import.types";
import type { OrderExportQueryParams } from "../utils/order-export-filters";

type InvoiceExportQueryParams = Pick<
    OrderQueryParams,
    "fromDate" | "keyword" | "paymentStatus" | "sort" | "toDate"
>;

function compactQuery(params: OrderQueryParams = {}) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
    );
}

export function getOrders(params: OrderQueryParams = {}) {
    return apiClient.get<OrderPageResponse<OrderSummaryResponse>>("/orders", {
        query: compactQuery(params)
    });
}

export function getOrderReturnEligibilityStats(keyword?: string) {
    return apiClient.get<OrderReturnEligibilityStatsResponse>(
        "/orders/return-eligibility-stats",
        {
            query: compactQuery({ keyword })
        }
    );
}

export function exportOrders(params: OrderExportQueryParams = {}) {
    return apiClient.download("/orders/export", {
        query: compactQuery(params)
    });
}

export function exportInvoices(params: InvoiceExportQueryParams = {}) {
    return apiClient.download("/orders/invoices/export/excel", {
        query: compactQuery(params)
    });
}

export function getOrderById(orderId: string) {
    return apiClient.get<OrderDetailResponse>(
        `/orders/${encodeURIComponent(orderId)}`
    );
}

export function getOrderStats(
    params: Pick<OrderQueryParams, "fromDate" | "toDate"> = {}
) {
    return apiClient.get<OrderStatsResponse>("/orders/stats", {
        query: compactQuery(params)
    });
}

export function updateOrderStatus(
    orderId: string,
    body: UpdateOrderStatusRequest
) {
    return apiClient.put<OrderDetailResponse, UpdateOrderStatusRequest>(
        `/orders/${encodeURIComponent(orderId)}/status`,
        body
    );
}

export function cancelOrder(orderId: string, body: CancelOrderRequest) {
    return apiClient.put<OrderDetailResponse, CancelOrderRequest>(
        `/orders/${encodeURIComponent(orderId)}/cancel`,
        body
    );
}

export const orderApi = {
    getOrders,
    getOrderReturnEligibilityStats,
    exportOrders,
    exportInvoices,
    getOrderById,
    getOrderStats,
    updateOrderStatus,
    cancelOrder
};

export function importOrders(input: OrderImportRequest) {
    return apiClient.post<OrderImportResponse, OrderImportRequest>("/orders/import", input, {
        // Nhập thật tạo từng đơn kèm thanh toán và giữ tồn kho, có thể mất vài chục giây.
        timeoutMs: input.dryRun ? 60_000 : 300_000,
        retry: false
    });
}
