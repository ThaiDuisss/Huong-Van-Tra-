package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.shared.enums.Status;

public record StatusRequest(@NotNull Status status) {
}
