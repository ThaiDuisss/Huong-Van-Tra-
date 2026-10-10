package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TopSellingProductReportResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String sortBy;
    private int limit;
    /** Các tổng bên dưới tính trên toàn bộ sản phẩm bán được trong kỳ, không chỉ các dòng trong {@code rows}. */
    private long totalProducts;
    private BigDecimal totalQuantitySold;
    private BigDecimal totalRevenue;
    private long totalOrders;
    private List<TopSellingProductResponse> rows;
}
