package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerTierAssignmentResponse(
        UUID id,
        UUID customerId,
        CustomerTierResponse tier,
        Integer qualificationYear,
        CustomerTierAssignmentReason assignmentReason,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveUntil,
        CustomerTierAssignmentStatus status,
        String note
) {
}
