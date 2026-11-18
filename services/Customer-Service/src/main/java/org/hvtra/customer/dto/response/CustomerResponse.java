package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * nextTier, remainingSpending và remainingOrderCount chỉ có ở API chi tiết (GET /api/customer/{id});
 * API danh sách trả null để không phải tính cho từng dòng.
 */
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
        LocalDateTime lastPurchaseDate,
        BigDecimal totalSpent,
        Integer totalOrderCount,
        Integer pointBalance,
        CustomerStatus status,
        Instant createdAt,
        CustomerTierResponse nextTier,
        BigDecimal remainingSpending,
        Integer remainingOrderCount
) {
}
