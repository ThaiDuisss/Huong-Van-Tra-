import { apiClient } from "../../../api/api-client";
import type { OrderPageResponse } from "../types/order.types";
import type {
    CreateCustomerReturnInspectionPayload,
    CreateOrderReturnPayload,
    CustomerReturnInspectionResponse,
    CustomerReturnQcSummaryResponse,
    OrderReturnQueryParams,
    OrderReturnSummaryResponse,
    OrderReturnResponse
} from "../types/order-return.types";

function compactQuery(params: OrderReturnQueryParams = {}) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
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

export function createCustomerReturnInspection(
    returnId: string,
    payload: CreateCustomerReturnInspectionPayload
) {
    return apiClient.post<
        CustomerReturnInspectionResponse,
        CreateCustomerReturnInspectionPayload
    >(
        `/inventory/customer-returns/${encodeURIComponent(returnId)}/inspections`,
        payload
    );
}

export function postCustomerReturnInspection(inspectionId: string) {
    return apiClient.post<CustomerReturnInspectionResponse, Record<string, never>>(
        `/inventory/customer-return-inspections/${encodeURIComponent(
            inspectionId
        )}/post`,
        {}
    );
}
