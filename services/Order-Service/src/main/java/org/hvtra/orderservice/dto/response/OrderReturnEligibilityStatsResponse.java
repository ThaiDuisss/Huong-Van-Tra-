package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderReturnEligibilityStatsResponse {
    private long total;
    private long eligible;
    private long ineligible;
}
