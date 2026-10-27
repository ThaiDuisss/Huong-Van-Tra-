package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;

import org.hvtra.orderservice.dto.response.SalesDashboardResponse;

import java.time.LocalDate;

public interface OrderStatisticReportService {

    String SORT_REVENUE = "revenue";
    String SORT_ORDERS = "orders";
    String SORT_QUANTITY = "quantity";
    int DEFAULT_LIMIT = 10;
    int MAX_LIMIT = 500;

    /** @param sortBy {@code revenue} (mặc định) hoặc {@code orders} */
    TopSalesStaffReportResponse getTopSalesStaff(LocalDate fromDate, LocalDate toDate, String sortBy, Integer limit);

    /** @param sortBy {@code quantity} (mặc định) hoặc {@code revenue} */
    TopSellingProductReportResponse getTopSellingProducts(
            LocalDate fromDate,
            LocalDate toDate,
            String sortBy,
            Integer limit
    );

    /** Số widget tối đa (top sản phẩm, top nhân viên) trên dashboard. */
    int DASHBOARD_TOP_LIMIT = 5;
    /** Dashboard gộp theo giờ nên giới hạn kỳ tối đa 31 ngày để không tải quá nhiều đơn. */
    int DASHBOARD_MAX_DAYS = 31;

    /**
     * Số liệu tổng hợp cho dashboard bán hàng. Thiếu {@code toDate} lấy hôm nay, thiếu {@code fromDate}
     * lấy bằng {@code toDate}.
     */
    SalesDashboardResponse getSalesDashboard(LocalDate fromDate, LocalDate toDate);
}
