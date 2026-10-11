package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.customer.model.enums.CustomerStatus;

public record ChangeCustomerStatusRequest(@NotNull CustomerStatus status) {
}
