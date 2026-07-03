package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerRequest(
        @Size(max = 50) String customerCode,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        LocalDate dateOfBirth,
        CustomerType customerType,
        UUID tierId,
        @DecimalMin("0.00") BigDecimal creditLimit,
        LocalDateTime lastPurchaseDate,
        @DecimalMin("0.00") BigDecimal totalSpent,
        @Min(0) Integer totalOrderCount,
        CustomerStatus status
) {
}
