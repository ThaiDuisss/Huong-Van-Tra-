package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TopSalesStaffReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String sortBy;
    private int limit;
    /** Các tổng bên dưới tính trên toàn bộ nhân viên trong kỳ, không chỉ các dòng trong {@code rows}. */
    private long totalStaff;
    private long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalItemsSold;
    private BigDecimal averageOrderValue;
    private long totalRefundedOrders;
    private List<TopSalesStaffResponse> rows;
}
