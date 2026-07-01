package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        CustomerType customerType,
        UUID tierId,
        String tierCode,
        String tierName,
        String tierColor,
        LocalDateTime lastPurchaseDate,
        BigDecimal totalSpent,
        Integer totalOrderCount,
        CustomerStatus status,
        Instant createdAt
) {
}
