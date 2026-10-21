package org.hvtra.orderservice.dto.response;

import org.hvtra.orderservice.domain.enums.ReturnReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderReturnItemEligibilityResponse(
        UUID orderItemId,
        ReturnReason reason,
        BigDecimal remainingQuantity,
        List<String> policy,
        List<OrderReturnEligibilityCheckResponse> checks
) {
}
