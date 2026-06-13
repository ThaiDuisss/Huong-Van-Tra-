import { QueryParams } from "../core/query-params";
import type { ApiClient } from "../core/types";
import type { PageResponse } from "./product.api";

export type OrderStatus =
    | "DRAFT"
    | "PENDING_PAYMENT"
    | "PAID"
    | "COMPLETED"
    | "CANCELLED";

export interface OrderItem {
    id: string;
    productVariantId: string;
    productName: string;
    variantName?: string;
    quantity: number;
    unitPrice: number;
    discountAmount: number;
    lineTotal: number;
}

export interface Order {
    id: string;
    orderCode: string;
    branchId: string;
    customerId?: string;
    employeeId: string;
    status: OrderStatus;
    items: OrderItem[];
    subtotal: number;
    discountAmount: number;
    totalAmount: number;
    createdAt: string;
}

export interface CreateOrderItemInput {
    productVariantId: string;
    quantity: number;
}

export interface CreateOrderInput {
    branchId: string;
    customerId?: string;
    items: CreateOrderItemInput[];
    note?: string;
}

export interface OrderQuery extends QueryParams {
    page?: number;
    size?: number;
    keyword?: string;
    branchId?: string;
    status?: OrderStatus;
    fromDate?: string;
    toDate?: string;
}

export function createOrderApi(apiClient: ApiClient) {
    return {
        getOrders(query: OrderQuery = {}) {
            return apiClient.get<PageResponse<Order>>(
                "/orders",
                {
                    query
                }
            );
        },

        getOrderById(id: string) {
            return apiClient.get<Order>(
                `/orders/${encodeURIComponent(id)}`
            );
        },

        createOrder(input: CreateOrderInput) {
            return apiClient.post<Order, CreateOrderInput>(
                "/orders",
                input
            );
        },

        cancelOrder(id: string, reason: string) {
            return apiClient.post<
                Order,
                { reason: string }
            >(
                `/orders/${encodeURIComponent(id)}/cancel`,
                {
                    reason
                }
            );
        },

        completeOrder(id: string) {
            return apiClient.post<Order>(
                `/orders/${encodeURIComponent(id)}/complete`
            );
        },

        downloadReceipt(id: string) {
            return apiClient.download(
                `/orders/${encodeURIComponent(id)}/receipt`
            );
        }
    };
}

export type OrderApi =
    ReturnType<typeof createOrderApi>;