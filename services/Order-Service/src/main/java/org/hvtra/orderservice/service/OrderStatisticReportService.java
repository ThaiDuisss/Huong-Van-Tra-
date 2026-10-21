package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.TopSalesStaffReportResponse;
import org.hvtra.orderservice.dto.response.TopSellingProductReportResponse;

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
}
