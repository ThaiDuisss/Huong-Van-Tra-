package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.enums.InventoryStatus;

public record InventoryStatusRequest(@NotNull InventoryStatus status) {
}
