package org.hvtra.deliveryservice.carrier.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CarrierSpecialRequest(
        @NotBlank String code,
        @Positive Integer quantity,
        String tierCode,
        @Valid List<CarrierRequestOption> options,
        @PositiveOrZero Integer pathIndex
) {
    public List<CarrierRequestOption> resolvedOptions() {
        return options == null ? List.of() : List.copyOf(options);
    }
}
