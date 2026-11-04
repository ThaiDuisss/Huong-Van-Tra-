package org.hvtra.iventoryservice.integration.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderReturnInventoryCommandResponse(
        UUID commandId,
        UUID returnId,
        String status,
        Boolean executionAllowed,
        List<Line> lines
) {
    public record Line(UUID returnItemId, UUID orderItemId, UUID productVariantId,
                       BigDecimal quantity, String result, UUID destinationLocationId) {
    }
}
