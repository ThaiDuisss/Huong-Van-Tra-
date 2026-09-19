package org.hvtra.iventoryservice.integration.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderReturnResponse(
        UUID id,
        String returnCode,
        UUID orderId,
        String orderCode,
        String customerName,
        UUID branchId,
        OrderReturnStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<OrderReturnItemResponse> items
) {
}
