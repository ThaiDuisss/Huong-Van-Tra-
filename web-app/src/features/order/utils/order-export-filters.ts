import type { OrderQueryParams } from "../types/order.types";

export type OrderExportQueryParams = Omit<OrderQueryParams, "page" | "size">;
type OrderExportFilterInput = {
    [Key in keyof OrderExportQueryParams]?:
        | OrderExportQueryParams[Key]
        | null;
};

function activeSelection(value?: string | null) {
    const normalized = value?.trim();
    if (!normalized || normalized.toUpperCase() === "ALL") {
        return undefined;
    }

    return normalized;
}

export function buildActiveOrderExportParams(
    filters: OrderExportFilterInput
): OrderExportQueryParams {
    return {
        fromDate: activeSelection(filters.fromDate),
        keyword: activeSelection(filters.keyword),
        orderType: activeSelection(filters.orderType),
        paymentStatus: activeSelection(filters.paymentStatus),
        sort: filters.sort ?? undefined,
        status: activeSelection(filters.status),
        toDate: activeSelection(filters.toDate)
    };
}
