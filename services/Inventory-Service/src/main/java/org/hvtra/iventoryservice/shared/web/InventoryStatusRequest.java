package org.hvtra.iventoryservice.shared.web;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

public record InventoryStatusRequest(@NotNull InventoryStatus status) {
}
