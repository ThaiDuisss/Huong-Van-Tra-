package org.hvtra.iventoryservice.production.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelProductionOrderRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
