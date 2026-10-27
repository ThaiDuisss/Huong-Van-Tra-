import { apiClient } from "../../../api/api-client";
import type { OrderPageResponse } from "../types/order.types";
import type {
    CreateOrderReturnPayload,
    CheckOrderReturnEligibilityPayload,
    OrderReturnEligibilityResponse,
    CustomerReturnQcSummaryResponse,
    OrderRefundDetailResponse,
    OrderRefundQueryParams,
    OrderRefundStatsResponse,
    OrderRefundSummaryResponse,
    OrderReturnQueryParams,
    OrderReturnRefundPreviewResponse,
    OrderReturnRefundResponse,
    OrderReturnSummaryResponse,
    OrderReturnResponse,
    OrderReturnTimelineResponse,
    OrderReturnInventoryDispositionResponse,
    SubmitReturnInventoryDispositionPayload
} from "../types/order-return.types";

function compactQuery(params: OrderReturnQueryParams | OrderRefundQueryParams = {}) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
    );
}

export function getOrderRefundList(params: OrderRefundQueryParams = {}) {
    return apiClient.get<OrderPageResponse<OrderRefundSummaryResponse>>(
        "/orders/refunds",
        {
            query: compactQuery(params)
        }
    );
}

export function getOrderRefundStats(
    params: Pick<OrderRefundQueryParams, "fromDate" | "keyword" | "toDate"> = {}
) {
    return apiClient.get<OrderRefundStatsResponse>("/orders/refunds/stats", {
        query: compactQuery(params)
    });
}

export function getOrderRefundDetail(returnId: string) {
    return apiClient.get<OrderRefundDetailResponse>(
        `/orders/refunds/${encodeURIComponent(returnId)}`
    );
}

export function getOrderRefundPreview(returnId: string) {
    return apiClient.get<OrderReturnRefundPreviewResponse>(
        `/orders/returns/${encodeURIComponent(returnId)}/refund-preview`
    );
}

export function refundOrderReturn(returnId: string) {
    return apiClient.post<OrderReturnRefundResponse, Record<string, never>>(
        `/orders/returns/${encodeURIComponent(returnId)}/refund`,
        {}
    );
}

export function getOrderReturnList(params: OrderReturnQueryParams = {}) {
    return apiClient.get<OrderPageResponse<OrderReturnSummaryResponse>>(
        "/orders/returns",
        {
            query: compactQuery(params)
        }
    );
}

export function getOrderReturnStatusCounts(
    params: Pick<OrderReturnQueryParams, "fromDate" | "keyword" | "toDate" | "warehouseActiveOnly"> = {}
) {
    return apiClient.get<Record<string, number>>(
        "/orders/returns/status-counts",
        {
            query: compactQuery(params)
        }
    );
}

export function checkOrderReturnEligibility(orderId: string, payload: CheckOrderReturnEligibilityPayload) {
    return apiClient.post<OrderReturnEligibilityResponse, CheckOrderReturnEligibilityPayload>(
        `/orders/${encodeURIComponent(orderId)}/returns/eligibility`,
        payload
    );
}

export function createOrderReturn(
    orderId: string,
    payload: CreateOrderReturnPayload
) {
    return apiClient.post<OrderReturnResponse, CreateOrderReturnPayload>(
        `/orders/${encodeURIComponent(orderId)}/returns`,
        payload
    );
}

export function getOrderReturns(orderId: string) {
    return apiClient.get<OrderReturnResponse[]>(
        `/orders/${encodeURIComponent(orderId)}/returns`
    );
}

export function getOrderReturnDetail(returnId: string) {
    return apiClient.get<OrderReturnResponse>(
        `/orders/returns/${encodeURIComponent(returnId)}`
    );
}

export function getOrderReturnTimeline(returnId: string) {
    return apiClient.get<OrderReturnTimelineResponse[]>(
        `/orders/returns/${encodeURIComponent(returnId)}/timeline`
    );
}

export function receiveOrderReturn(returnId: string) {
    return apiClient.put<OrderReturnResponse, Record<string, never>>(
        `/orders/returns/${encodeURIComponent(returnId)}/receive`,
        {}
    );
}

export function cancelOrderReturn(returnId: string) {
    return apiClient.put<OrderReturnResponse, Record<string, never>>(
        `/orders/returns/${encodeURIComponent(returnId)}/cancel`,
        {}
    );
}

export function getCustomerReturnQcSummary(returnId: string) {
    return apiClient.get<CustomerReturnQcSummaryResponse>(
        `/inventory/customer-returns/${encodeURIComponent(returnId)}/qc-summary`
    );
}

export function submitReturnInventoryDisposition(
    returnId: string,
    payload: SubmitReturnInventoryDispositionPayload
) {
    return apiClient.post<
        OrderReturnInventoryDispositionResponse,
        SubmitReturnInventoryDispositionPayload
    >(
        `/orders/returns/${encodeURIComponent(returnId)}/inventory-disposition`,
        payload
    );
}

export function getReturnInventoryDisposition(
    returnId: string,
    commandId: string
) {
    return apiClient.get<OrderReturnInventoryDispositionResponse>(
        `/orders/returns/${encodeURIComponent(
            returnId
        )}/inventory-dispositions/${encodeURIComponent(commandId)}`
    );
}
