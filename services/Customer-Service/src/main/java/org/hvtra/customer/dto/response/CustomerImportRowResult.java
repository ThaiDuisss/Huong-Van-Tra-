package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerImportRowStatus;

import java.util.UUID;

public record CustomerImportRowResult(
        int rowNumber,
        CustomerImportRowStatus status,
        String message,
        UUID customerId,
        String customerCode,
        String fullName,
        String phone
) {
}
