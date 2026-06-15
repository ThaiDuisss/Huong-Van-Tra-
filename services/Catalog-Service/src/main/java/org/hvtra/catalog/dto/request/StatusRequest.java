package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.catalog.enums.Status;

public record StatusRequest(@NotNull Status status) {
}
