package org.hvtra.deliveryservice.carrier.model;

import jakarta.validation.constraints.NotBlank;

public record CarrierRequestOption(
        @NotBlank String code,
        String selectedValue
) {
}
