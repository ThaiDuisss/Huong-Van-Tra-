package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerCode,
        String fullName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        UUID tierId,
        String tierCode,
        String tierName,
        String tierColor,
        BigDecimal creditLimit,
        LocalDate lastPurchaseDate,
        BigDecimal totalSpent,
        CustomerStatus status,
        Instant createdAt
) {
}
