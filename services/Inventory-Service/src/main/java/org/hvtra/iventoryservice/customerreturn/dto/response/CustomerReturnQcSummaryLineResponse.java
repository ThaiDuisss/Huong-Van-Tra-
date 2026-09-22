package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerReturnQcSummaryLineResponse(
        UUID returnItemId,
        UUID orderItemId,
        UUID productVariantId,
        BigDecimal returnedQuantity,
        BigDecimal postedQcQuantity,
        BigDecimal remainingQcQuantity,
        CustomerReturnQcStatus status,
        OrderReturnCondition condition,
        String reason,
        List<CustomerReturnQcDispositionSummaryResponse> dispositions
) {
}
