package org.hvtra.orderservice.dto.response;

import java.util.List;

public record OrderReturnEligibilityResponse(
        boolean eligible,
        List<OrderReturnEligibilityCheckResponse> checks,
        List<OrderReturnItemEligibilityResponse> items
) {
}
