package org.hvtra.orderservice.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CustomerPurchaseResponse {
    private UUID id;
    private String customerCode;
    private String fullName;
    private String phone;
    private String email;
    private LocalDate dateOfBirth;
    private UUID tierId;
    private String tierCode;
    private String tierName;
    private String tierColor;
    private BigDecimal creditLimit;
    private LocalDate lastPurchaseDate;
    private BigDecimal totalSpent;
    private String status;
    private Instant createdAt;
}
