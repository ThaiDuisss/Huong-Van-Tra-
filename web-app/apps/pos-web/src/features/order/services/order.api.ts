import { apiClient } from "../../../api/api-client";
import type {
    CancelOrderRequest,
    OrderDetailResponse,
    OrderPageResponse,
    OrderQueryParams,
    OrderStatsResponse,
    OrderSummaryResponse,
    UpdateOrderStatusRequest
} from "../types/order.types";

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
    getOrderById,
    getOrderStats,
    updateOrderStatus,
    cancelOrder
};
