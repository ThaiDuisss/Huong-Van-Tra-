package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TodaySalesSummaryResponse {
    private BigDecimal todayRevenue;
    private long totalOrders;
    private long completedOrders;
    private long pendingPaymentOrders;
    private BigDecimal averageOrderValue;
    private BigDecimal pendingAmount;
}
