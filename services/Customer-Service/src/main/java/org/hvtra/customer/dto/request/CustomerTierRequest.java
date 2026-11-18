package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.customer.model.enums.CustomerTierStatus;

import java.math.BigDecimal;

public record CustomerTierRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.00") BigDecimal minSpending,
        @Min(0) Integer minOrderCount,
        @DecimalMin(value = "0.00", inclusive = false) @DecimalMax("999.99") @Digits(integer = 3, fraction = 2)
        BigDecimal pointMultiplier,
        @NotNull CustomerTierStatus status,
        @Size(max = 7) String colorCode
) {
}
