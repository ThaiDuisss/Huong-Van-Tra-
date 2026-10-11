package org.hvtra.orderservice.service;

import java.time.LocalDate;

public interface OrderStatisticReportExportService {

    byte[] exportTopSalesStaff(LocalDate fromDate, LocalDate toDate, String sortBy, Integer limit);

    /** Báo cáo sản phẩm bán chạy kèm tồn kho hiện tại (để trống cột tồn kho nếu Inventory-Service lỗi). */
    byte[] exportTopSellingProducts(LocalDate fromDate, LocalDate toDate, String sortBy, Integer limit);
}
