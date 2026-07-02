package org.hvtra.customer.service;

import org.hvtra.customer.config.LoyaltyTierProperties;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.impl.TierAssignmentServiceImpl;
import org.hvtra.customer.service.impl.TierQualificationServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyTierDomainTest {

    @Test
    void purchaseFactoryCreatesPositivePurchaseWithOrderReference() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 15, 10, 0);

        CustomerPurchaseTransaction transaction = CustomerPurchaseTransaction.purchase(
                customerId,
                orderId,
                BigDecimal.valueOf(100_000),
                occurredAt
        );

        assertThat(transaction.getCustomerId()).isEqualTo(customerId);
        assertThat(transaction.getReferenceId()).isEqualTo(orderId);
        assertThat(transaction.getTransactionType()).isEqualTo(CustomerPurchaseTransactionType.PURCHASE);
        assertThat(transaction.getAmount()).isEqualByComparingTo("100000");
        assertThat(transaction.getOrderCountDelta()).isEqualTo(1);
    }

    @Test
    void partialRefundSubtractsSpentButKeepsOrderCount() {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(UUID.randomUUID(), 2026);
        metric.addPurchase(BigDecimal.valueOf(500_000));

        metric.applyRefund(BigDecimal.valueOf(-120_000), 0);

        assertThat(metric.getTotalSpent()).isEqualByComparingTo("380000");
        assertThat(metric.getTotalOrderCount()).isEqualTo(1);
    }

    @Test
    void fullRefundSubtractsSpentAndOrderCountWithoutGoingNegative() {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(UUID.randomUUID(), 2026);
        metric.addPurchase(BigDecimal.valueOf(100_000));

        metric.applyRefund(BigDecimal.valueOf(-500_000), -1);
        metric.applyRefund(BigDecimal.valueOf(-10_000), -1);

        assertThat(metric.getTotalSpent()).isEqualByComparingTo("0");
        assertThat(metric.getTotalOrderCount()).isZero();
    }

    @Test
    void qualificationRequiresBothSpendingAndOrderCountThenFallsBackLowestTier() {
        CustomerTierRepository tierRepository = mock(CustomerTierRepository.class);
        CustomerTier member = tier("MEMBER", 0, 0);
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        when(tierRepository.findHighestQualifiedTier(BigDecimal.valueOf(1_000_000), 2)).thenReturn(Optional.empty());
        when(tierRepository.findLowestActiveTier()).thenReturn(Optional.of(member));
        when(tierRepository.findHighestQualifiedTier(BigDecimal.valueOf(1_000_000), 3)).thenReturn(Optional.of(silver));

        TierQualificationService service = new TierQualificationServiceImpl(tierRepository);

        assertThat(service.resolveQualifiedTier(BigDecimal.valueOf(1_000_000), 2).getCode()).isEqualTo("MEMBER");
        assertThat(service.resolveQualifiedTier(BigDecimal.valueOf(1_000_000), 3).getCode()).isEqualTo("SILVER");
    }

    @Test
    void calculateYearlyQualificationEffectiveUntilUsesGracePeriodConfiguration() {
        LoyaltyTierProperties properties = new LoyaltyTierProperties();
        properties.setGracePeriodMonths(1);
        TierAssignmentService service = new TierAssignmentServiceImpl(
                properties,
                mock(CustomerTierAssignmentRepository.class),
                mock(CustomerRepository.class)
        );

        assertThat(service.calculateYearlyQualificationEffectiveUntil(2026))
                .isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
    }

    @Test
    void replacingTierClosesOldAssignmentAndUpdatesCustomerTier() {
        CustomerTierAssignmentRepository assignmentRepository = mock(CustomerTierAssignmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        LoyaltyTierProperties properties = new LoyaltyTierProperties();
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        CustomerTier member = tier("MEMBER", 0, 0);
        CustomerTier silver = tier("SILVER", 1_000_000, 3);
        customer.setTier(member);
        CustomerTierAssignment active = CustomerTierAssignment.active(
                customer,
                member,
                2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                null
        );
        when(assignmentRepository.findActiveByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.of(active));
        when(assignmentRepository.save(org.mockito.ArgumentMatchers.any(CustomerTierAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TierAssignmentService service = new TierAssignmentServiceImpl(properties, assignmentRepository, customerRepository);
        CustomerTierAssignment replacement = service.replaceActiveTier(
                customer,
                silver,
                2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION,
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2027, 2, 1, 0, 0),
                "Yearly qualification"
        );

        assertThat(active.getStatus()).isEqualTo(CustomerTierAssignmentStatus.REPLACED);
        assertThat(replacement.getStatus()).isEqualTo(CustomerTierAssignmentStatus.ACTIVE);
        assertThat(customer.getTier()).isEqualTo(silver);
        verify(customerRepository).save(customer);
    }

    private CustomerTier tier(String code, int minSpending, int minOrderCount) {
        CustomerTier tier = new CustomerTier();
        tier.setId(UUID.randomUUID());
        tier.setCode(code);
        tier.setName(code);
        tier.setMinSpending(BigDecimal.valueOf(minSpending));
        tier.setMinOrderCount(minOrderCount);
        tier.setColorCode("#000000");
        return tier;
    }
}
