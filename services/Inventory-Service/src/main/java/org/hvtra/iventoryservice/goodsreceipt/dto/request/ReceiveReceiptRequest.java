package org.hvtra.iventoryservice.goodsreceipt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReceiveReceiptRequest(
        @NotEmpty(message = "Items cannot be empty")
        @Valid
        List<ReceiveItemRequest> items
) {
    public record ReceiveItemRequest(
            @NotNull(message = "Line ID is required")
            UUID lineId,

            @NotNull(message = "Received quantity is required")
            @PositiveOrZero(message = "Received quantity cannot be negative")
            BigDecimal receivedQuantity,

            String note
    ) {
    }
}
