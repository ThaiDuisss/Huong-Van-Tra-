import { apiClient } from "../../../api/api-client";
import type { OrderPageResponse } from "../types/order.types";
import type {
    OrderReturnReportDetail,
    OrderReturnReportQueryParams,
    OrderReturnReportSummary
} from "../types/order-return-report.types";

function compactQuery(params: OrderReturnReportQueryParams = {}) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
    );
}

export function getReturnRefundReportSummary(
    params: Pick<OrderReturnReportQueryParams, "branchId" | "fromDate" | "toDate"> = {}
) {
    return apiClient.get<OrderReturnReportSummary>(
        "/orders/reports/returns/summary",
        { query: compactQuery(params) }
    );
}

export function getReturnRefundReportDetails(
    params: OrderReturnReportQueryParams = {}
) {
    return apiClient.get<OrderPageResponse<OrderReturnReportDetail>>(
        "/orders/reports/returns",
        { query: compactQuery(params) }
    );
}
