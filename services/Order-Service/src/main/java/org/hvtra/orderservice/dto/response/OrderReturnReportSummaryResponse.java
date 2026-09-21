package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OrderReturnReportSummaryResponse {
    private long totalReturns;
    private long completedReturns;
    private long processingReturns;
    private long cancelledReturns;
    private long ordersWithReturns;
    private BigDecimal returnedQuantity;
    private long refundedReturnCount;
    private BigDecimal totalRefundAmount;
    private BigDecimal averageRefundAmount;
    private List<OrderReturnStatusBreakdownResponse> statusBreakdown;
    private List<OrderReturnReportReasonBreakdownResponse> reasonBreakdown;
    private List<OrderReturnReportQcBreakdownResponse> qcBreakdown;
}
