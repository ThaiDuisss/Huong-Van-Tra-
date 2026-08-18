package org.hvtra.iventoryservice.shared.web;

import jakarta.validation.constraints.NotNull;
import org.hvtra.iventoryservice.domain.enums.Status;

public record StatusRequest(@NotNull Status status) {
}
