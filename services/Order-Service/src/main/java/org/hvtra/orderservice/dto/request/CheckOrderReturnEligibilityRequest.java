package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CheckOrderReturnEligibilityRequest(
        // Item fields are evaluated by the shared resolver so invalid quantities/reasons
        // produce individual failed checks instead of a generic Bean Validation error.
        @NotNull List<CreateOrderReturnItemRequest> items
) {
}
