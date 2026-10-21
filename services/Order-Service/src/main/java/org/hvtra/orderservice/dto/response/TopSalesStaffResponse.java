package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TopSalesStaffResponse {
    private int rank;
    /** Null khi đơn hàng không ghi nhận thu ngân. */
    private UUID cashierId;
    private String cashierName;
    private long orderCount;
    /** Doanh thu gộp: tổng finalAmount của các đơn đã thanh toán (chưa trừ tiền hoàn). */
    private BigDecimal revenue;
    private BigDecimal averageOrderValue;
    private BigDecimal itemsSold;
    /** Số đơn trong kỳ đã hoàn tiền một phần hoặc toàn bộ. */
    private long refundedOrderCount;
}
