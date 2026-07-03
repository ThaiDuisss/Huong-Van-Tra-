package org.hvtra.customer.service;

import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;

import java.time.LocalDateTime;

public interface TierAssignmentService {
    CustomerTierAssignment assignInitialTier(Customer customer, CustomerTier initialTier, LocalDateTime assignedAt);

    CustomerTierAssignment replaceActiveTier(
            Customer customer,
            CustomerTier newTier,
            Integer qualificationYear,
            CustomerTierAssignmentReason reason,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            String note
    );

    LocalDateTime calculateYearlyQualificationEffectiveUntil(Integer qualificationYear);
}
