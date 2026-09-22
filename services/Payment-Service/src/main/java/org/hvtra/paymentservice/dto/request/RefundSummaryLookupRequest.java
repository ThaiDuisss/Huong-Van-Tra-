package org.hvtra.paymentservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RefundSummaryLookupRequest(
        @NotEmpty List<@Valid RefundSummaryLookupItem> items
) {

    public record RefundSummaryLookupItem(
            @NotNull UUID returnId,
            @NotNull UUID orderId
    ) {
    }
}
