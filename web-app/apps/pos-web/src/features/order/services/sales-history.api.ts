import { apiClient } from "../../../api/api-client";
import {
    SALES_PAYMENT_METHOD_OPTIONS,
    SALES_STATUS_FILTER_OPTIONS
} from "../constants/sales-history-options";
import type {
    SalesHistoryFilters,
    SalesHistoryOrder,
    SalesHistoryPageResponse,
    SalesHistorySummary,
    SalesOrderDetail
} from "../types/sales-history.types";

interface TodaySalesQuery {
    filters: SalesHistoryFilters;
    page?: number;
    size?: number;
}

type QueryValue = string | number | null | undefined;

function cleanParams(query: Record<string, QueryValue>) {
    return Object.fromEntries(
        Object.entries(query).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            if (typeof value !== "string") {
                return true;
            }

            const normalizedValue = value.trim();

            return normalizedValue !== "" && normalizedValue !== "ALL";
        })
    );
}

function mapStatusParam(status: string) {
    if (status === "ALL") {
        return null;
    }

    if (status === "UNPAID" || status === "WAITING_PAYMENT") {
        return "PENDING_PAYMENT";
    }

    if (status === "PAID") {
        return "COMPLETED";
    }

    return status;
}

function mapPaymentMethodParam(paymentMethod: string) {
    if (paymentMethod === "ALL") {
        return null;
    }

    if (paymentMethod === "TRANSFER" || paymentMethod === "QR_CODE") {
        return "BANK_TRANSFER";
    }

    return paymentMethod;
}

function buildTodaySalesQuery({
    filters,
    page = 0,
    size = 20
}: TodaySalesQuery) {
    const statusFilter = SALES_STATUS_FILTER_OPTIONS.find(
        (option) => option.key === filters.status
    )?.status;
    const paymentMethodFilter = SALES_PAYMENT_METHOD_OPTIONS.find(
        (option) => option.key === filters.paymentMethod
    )?.paymentMethod;

    return cleanParams({
        status: mapStatusParam(statusFilter ?? filters.status),
        paymentMethod: mapPaymentMethodParam(
            paymentMethodFilter ?? filters.paymentMethod
        ),
        page,
        size
    });
}

export function getTodaySalesSummary() {
    return apiClient.get<SalesHistorySummary>("/orders/today-sales/summary");
}

export function getTodaySales(query: TodaySalesQuery) {
    const params = buildTodaySalesQuery(query);

    console.log("today sales params", params);

    return apiClient.get<SalesHistoryPageResponse<SalesHistoryOrder>>(
        "/orders/today-sales",
        {
            query: params
        }
    );
}

export function getSalesOrderDetail(orderId: string) {
    return apiClient.get<SalesOrderDetail>(
        `/orders/${encodeURIComponent(orderId)}`
    );
}
