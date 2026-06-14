package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.enums.Status;

public record StatusRequest(@NotNull Status status) {
}
