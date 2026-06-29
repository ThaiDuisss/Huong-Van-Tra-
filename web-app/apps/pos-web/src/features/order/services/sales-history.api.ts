import { apiClient } from "../../../api/api-client";
import {
    getSalesPaymentMethod,
    getSalesStatus,
    sortSalesOrdersByBusinessPriority
} from "../constants/sales-history-options";
import { mockSalesHistoryOrders } from "../mock/sales-history.mock";
import type {
    SalesHistoryFilterCounts,
    SalesHistoryFilters,
    SalesHistoryOrder,
    SalesHistoryResponse
} from "../types/sales-history.types";

function compactQuery(params: SalesHistoryFilters) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
    );
}

function matchesFilters(order: SalesHistoryOrder, filters: SalesHistoryFilters) {
    const statusMatches =
        filters.status === "ALL" || getSalesStatus(order) === filters.status;

    const paymentMatches =
        filters.paymentMethod === "ALL" ||
        getSalesPaymentMethod(order) === filters.paymentMethod;

    return statusMatches && paymentMatches;
}

function isToday(value: string) {
    const date = new Date(value);
    const today = new Date();

    return (
        date.getFullYear() === today.getFullYear() &&
        date.getMonth() === today.getMonth() &&
        date.getDate() === today.getDate()
    );
}

function calculateSalesSummary(orders: SalesHistoryOrder[]) {
    const completedOrders = orders.filter((order) => getSalesStatus(order) === "PAID");
    const netRevenue = completedOrders.reduce(
        (total, order) => total + order.finalAmount,
        0
    );
    const pendingPaymentOrders = orders.filter(
        (order) => getSalesStatus(order) === "UNPAID"
    );

    return {
        totalOrders: orders.length,
        completedOrders: completedOrders.length,
        pendingPaymentOrders: pendingPaymentOrders.length,
        cancelledOrders: orders.filter((order) => getSalesStatus(order) === "CANCELLED")
            .length,
        grossAmount: orders.reduce((total, order) => total + order.totalAmount, 0),
        discountAmount: orders.reduce(
            (total, order) => total + order.discountAmount,
            0
        ),
        netRevenue,
        averageOrderValue:
            completedOrders.length > 0
                ? Math.round(netRevenue / completedOrders.length)
                : 0,
        totalItemsSold: completedOrders.reduce(
            (total, order) => total + order.totalItems,
            0
        ),
        receivableAmount: pendingPaymentOrders.reduce(
            (total, order) =>
                total + (order.debtAmount > 0 ? order.debtAmount : order.finalAmount),
            0
        )
    };
}

function getFilterCounts(orders: SalesHistoryOrder[]): SalesHistoryFilterCounts {
    return orders.reduce<SalesHistoryFilterCounts>(
        (counts, order) => {
            const status = getSalesStatus(order);
            const paymentMethod = getSalesPaymentMethod(order);

            counts.status.ALL += 1;
            counts.status[status] += 1;
            counts.paymentMethod.ALL += 1;
            counts.paymentMethod[paymentMethod] += 1;

            return counts;
        },
        {
            status: {
                ALL: 0,
                UNPAID: 0,
                PAID: 0,
                CANCELLED: 0
            },
            paymentMethod: {
                ALL: 0,
                CASH: 0,
                BANK_TRANSFER: 0,
                DEBT: 0
            }
        }
    );
}

function getMockTodaySalesHistory(filters: SalesHistoryFilters) {
    const todayOrders = mockSalesHistoryOrders.filter((order) =>
        isToday(order.createdAt)
    );
    const orders = sortSalesOrdersByBusinessPriority(
        todayOrders.filter((order) => matchesFilters(order, filters))
    );

    return {
        summary: calculateSalesSummary(todayOrders),
        filterCounts: getFilterCounts(todayOrders),
        orders
    };
}

export function getTodaySalesHistory(filters: SalesHistoryFilters) {
    return new Promise<SalesHistoryResponse>((resolve) => {
        window.setTimeout(() => {
            resolve(getMockTodaySalesHistory(filters));
        }, 280);
    });
}

export function getTodaySalesHistoryFromApi(filters: SalesHistoryFilters) {
    return apiClient.get<SalesHistoryResponse>(
        "/orders/sales-history/today",
        {
            query: compactQuery(filters)
        }
    );
}
