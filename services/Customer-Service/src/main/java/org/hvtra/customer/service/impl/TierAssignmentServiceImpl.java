package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.config.LoyaltyTierProperties;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.service.TierAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TierAssignmentServiceImpl implements TierAssignmentService {
    private final LoyaltyTierProperties properties;
    private final CustomerTierAssignmentRepository assignmentRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public CustomerTierAssignment assignInitialTier(Customer customer, CustomerTier initialTier, LocalDateTime assignedAt) {
        return replaceActiveTier(customer, initialTier, null, CustomerTierAssignmentReason.INITIAL_TIER, assignedAt, null, "Initial tier");
    }

    @Override
    @Transactional
    public CustomerTierAssignment replaceActiveTier(Customer customer, CustomerTier newTier, Integer qualificationYear,
                                                   CustomerTierAssignmentReason reason, LocalDateTime effectiveFrom,
                                                   LocalDateTime effectiveUntil, String note) {
        CustomerTierAssignment active = assignmentRepository.findActiveByCustomerIdForUpdate(customer.getId()).orElse(null);
        if (active != null && Objects.equals(active.getTier().getId(), newTier.getId())) {
            return active;
        }
        if (active != null) {
            active.replace(effectiveFrom);
            assignmentRepository.save(active);
        }

        CustomerTierAssignment replacement = CustomerTierAssignment.active(customer, newTier, qualificationYear, reason, effectiveFrom, effectiveUntil, note);
        customer.changeTier(newTier);
        customerRepository.save(customer);
        return assignmentRepository.save(replacement);
    }

    @Override
    public LocalDateTime calculateYearlyQualificationEffectiveUntil(Integer qualificationYear) {
        return LocalDate.of(qualificationYear + 1, 1, 1)
                .plusMonths(properties.getGracePeriodMonths())
                .atStartOfDay();
    }
}
