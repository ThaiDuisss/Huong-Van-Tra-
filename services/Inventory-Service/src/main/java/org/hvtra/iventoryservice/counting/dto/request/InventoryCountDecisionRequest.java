package org.hvtra.iventoryservice.counting.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InventoryCountDecisionRequest(@NotBlank String reason) {
}
