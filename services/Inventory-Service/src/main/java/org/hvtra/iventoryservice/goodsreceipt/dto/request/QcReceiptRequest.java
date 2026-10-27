package org.hvtra.iventoryservice.goodsreceipt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QcReceiptRequest(
        @NotEmpty(message = "QC items cannot be empty")
        @Valid
        List<QcItemRequest> items
) {
    public record QcItemRequest(
            @NotNull(message = "Line ID is required")
            UUID lineId,

            @NotNull(message = "Accepted quantity is required")
            @PositiveOrZero(message = "Accepted quantity cannot be negative")
            BigDecimal acceptedQuantity,

            @NotNull(message = "Rejected quantity is required")
            @PositiveOrZero(message = "Rejected quantity cannot be negative")
            BigDecimal rejectedQuantity,

            @Valid
            List<BatchAllocationInput> batches
    ) {
    }

    public record BatchAllocationInput(
            String batchCode,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            @NotNull(message = "Batch quantity is required")
            @PositiveOrZero(message = "Batch quantity cannot be negative")
            BigDecimal quantity,
            String supplierLot
    ) {
    }
}
