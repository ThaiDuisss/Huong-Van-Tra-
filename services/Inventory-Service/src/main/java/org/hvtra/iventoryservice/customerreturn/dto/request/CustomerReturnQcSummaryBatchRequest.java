package org.hvtra.iventoryservice.customerreturn.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnCondition;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CustomerReturnQcSummaryBatchRequest(
        @NotEmpty List<@Valid CustomerReturnSnapshot> returns
) {

    public record CustomerReturnSnapshot(
            @NotNull UUID returnId,
            String returnCode,
            OrderReturnStatus returnStatus,
            UUID branchId,
            UUID orderId,
            String orderCode,
            @NotEmpty List<@Valid CustomerReturnLineSnapshot> lines
    ) {
    }

    public record CustomerReturnLineSnapshot(
            @NotNull UUID returnItemId,
            UUID orderItemId,
            UUID productVariantId,
            @NotNull BigDecimal quantity,
            OrderReturnCondition condition,
            String reason
    ) {
    }
}
