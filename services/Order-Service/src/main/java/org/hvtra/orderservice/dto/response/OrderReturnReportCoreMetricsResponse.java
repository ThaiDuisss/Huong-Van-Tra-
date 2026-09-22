package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderReturnReportCoreMetricsResponse {
    private Long totalReturns;
    private Long completedReturns;
    private Long processingReturns;
    private Long cancelledReturns;
    private Long ordersWithReturns;
}
