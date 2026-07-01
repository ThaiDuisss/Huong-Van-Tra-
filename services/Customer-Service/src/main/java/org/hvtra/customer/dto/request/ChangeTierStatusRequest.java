package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.customer.model.enums.CustomerTierStatus;

public record ChangeTierStatusRequest(@NotNull CustomerTierStatus status) {
}
