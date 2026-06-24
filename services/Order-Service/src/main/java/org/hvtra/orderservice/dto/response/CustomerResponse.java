package org.hvtra.orderservice.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CustomerResponse {

    private UUID id;

    private String customerCode;

    private String fullName;

    private String phone;

    private String email;

    private LocalDate dateOfBirth;

    private UUID tierId;

    private String tierCode;

    private String tierName;

    private BigDecimal creditLimit;

    private String status;
}