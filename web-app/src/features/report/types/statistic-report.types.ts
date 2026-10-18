/** Số thập phân từ backend (BigDecimal) có thể về dạng number hoặc string. */
export type DecimalValue = number | string;

export type StaffSortBy = "revenue" | "orders";
export type ProductSortBy = "quantity" | "revenue";

export interface StatisticReportQueryParams<TSort extends string> {
    /** yyyy-MM-dd, tính theo ngày tạo đơn. */
    fromDate?: string;
    /** yyyy-MM-dd, bao gồm trọn ngày này. */
    toDate?: string;
    limit?: number;
    sortBy?: TSort;
}

export interface TopSalesStaffRow {
    averageOrderValue: DecimalValue;
    /** null khi đơn không ghi nhận thu ngân. */
    cashierId: string | null;
    cashierName: string | null;
    itemsSold: DecimalValue;
    orderCount: number;
    rank: number;
    refundedOrderCount: number;
    revenue: DecimalValue;
}

export interface TopSalesStaffReport {
    averageOrderValue: DecimalValue;
    fromDate: string | null;
    limit: number;
    rows: TopSalesStaffRow[];
    sortBy: StaffSortBy;
    toDate: string | null;
    totalItemsSold: DecimalValue;
    totalOrders: number;
    totalRefundedOrders: number;
    totalRevenue: DecimalValue;
    totalStaff: number;
}

export interface TopSellingProductRow {
    orderCount: number;
    productName: string | null;
    productVariantId: string;
    quantitySold: DecimalValue;
    rank: number;
    returnedQuantity: DecimalValue;
    revenue: DecimalValue;
    sku: string | null;
    variantName: string | null;
}

export interface TopSellingProductReport {
    fromDate: string | null;
    limit: number;
    rows: TopSellingProductRow[];
    sortBy: ProductSortBy;
    toDate: string | null;
    totalOrders: number;
    totalProducts: number;
    totalQuantitySold: DecimalValue;
    totalRevenue: DecimalValue;
}
