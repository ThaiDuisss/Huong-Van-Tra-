import { apiClient } from "../../../api/api-client";
import type {
    ProductSortBy,
    StaffSortBy,
    StatisticReportQueryParams,
    TopSalesStaffReport,
    TopSellingProductReport
} from "../types/statistic-report.types";

function compactQuery<TSort extends string>(
    params: StatisticReportQueryParams<TSort> = {}
) {
    return Object.fromEntries(
        Object.entries(params).filter(([, value]) => {
            if (value === null || value === undefined) {
                return false;
            }

            return typeof value !== "string" || value.trim() !== "";
        })
    );
}

export function getTopSalesStaffReport(
    params: StatisticReportQueryParams<StaffSortBy> = {}
) {
    return apiClient.get<TopSalesStaffReport>(
        "/orders/reports/top-sales-staff",
        { query: compactQuery(params) }
    );
}

export function exportTopSalesStaffReport(
    params: StatisticReportQueryParams<StaffSortBy> = {}
) {
    return apiClient.download("/orders/reports/top-sales-staff/export", {
        query: compactQuery(params)
    });
}

export function getTopSellingProductsReport(
    params: StatisticReportQueryParams<ProductSortBy> = {}
) {
    return apiClient.get<TopSellingProductReport>(
        "/orders/reports/top-selling-products",
        { query: compactQuery(params) }
    );
}

/** Báo cáo tồn kho: sản phẩm bán chạy kèm tồn kho hiện tại. */
export function exportTopSellingProductsReport(
    params: StatisticReportQueryParams<ProductSortBy> = {}
) {
    return apiClient.download("/orders/reports/top-selling-products/export", {
        query: compactQuery(params)
    });
}
