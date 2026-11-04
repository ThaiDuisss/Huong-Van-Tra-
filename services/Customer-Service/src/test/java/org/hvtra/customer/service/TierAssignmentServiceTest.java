package org.hvtra.customer.service;

import org.hvtra.customer.config.LoyaltyTierProperties;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.service.impl.TierAssignmentServiceImpl;
import org.hvtra.customer.service.impl.TierQualificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierAssignmentServiceTest {

    private static final LocalDateTime ASSIGNED_AT = LocalDateTime.of(2026, 8, 15, 10, 0);

    private CustomerTierAssignmentRepository assignmentRepository;
    private CustomerRepository customerRepository;
    private CustomerTierRepository tierRepository;
    private LoyaltyTierProperties properties;
    private TierAssignmentServiceImpl assignmentService;
    private TierQualificationServiceImpl qualificationService;

    private Customer customer;
    private CustomerTier member;
    private CustomerTier silver;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(CustomerTierAssignmentRepository.class);
        customerRepository = mock(CustomerRepository.class);
        tierRepository = mock(CustomerTierRepository.class);
        properties = new LoyaltyTierProperties();
        assignmentService = new TierAssignmentServiceImpl(properties, assignmentRepository, customerRepository);
        qualificationService = new TierQualificationServiceImpl(tierRepository);

        customer = new Customer();
        customer.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        member = tier("MEMBER", 0, 0);
        silver = tier("SILVER", 1_000_000, 3);
        when(assignmentRepository.save(any(CustomerTierAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void assignInitialTierCreatesActiveInitialAssignment() {
        when(assignmentRepository.findActiveByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.empty());

        CustomerTierAssignment assignment = assignmentService.assignInitialTier(customer, member, ASSIGNED_AT);

        assertThat(assignment.getStatus()).isEqualTo(CustomerTierAssignmentStatus.ACTIVE);
        assertThat(assignment.getAssignmentReason()).isEqualTo(CustomerTierAssignmentReason.INITIAL_TIER);
        assertThat(assignment.getTier()).isEqualTo(member);
        assertThat(assignment.getEffectiveFrom()).isEqualTo(ASSIGNED_AT);
        assertThat(assignment.getEffectiveUntil()).isNull();
        assertThat(assignment.getNote()).isEqualTo("Initial tier");
        assertThat(customer.getTier()).isEqualTo(member);
        verify(customerRepository).save(customer);
    }

    @Test
    void replaceActiveTierKeepsCurrentAssignmentWhenTierIsUnchanged() {
        CustomerTierAssignment active = CustomerTierAssignment.active(customer, silver, 2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION, ASSIGNED_AT, null, null);
        when(assignmentRepository.findActiveByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.of(active));

        CustomerTierAssignment result = assignmentService.replaceActiveTier(customer, silver, 2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION, ASSIGNED_AT.plusDays(1), null, "Yearly qualification");

        assertThat(result).isSameAs(active);
        assertThat(active.getStatus()).isEqualTo(CustomerTierAssignmentStatus.ACTIVE);
        verify(assignmentRepository, never()).save(any());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void replaceActiveTierClosesOldAssignmentAtNewEffectiveFrom() {
        CustomerTierAssignment active = CustomerTierAssignment.active(customer, member, null,
                CustomerTierAssignmentReason.INITIAL_TIER, ASSIGNED_AT.minusMonths(3), null, "Initial tier");
        when(assignmentRepository.findActiveByCustomerIdForUpdate(customer.getId())).thenReturn(Optional.of(active));

        CustomerTierAssignment result = assignmentService.replaceActiveTier(customer, silver, 2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION, ASSIGNED_AT, LocalDateTime.of(2027, 2, 1, 0, 0),
                "Yearly qualification");

        assertThat(active.getStatus()).isEqualTo(CustomerTierAssignmentStatus.REPLACED);
        assertThat(active.getEffectiveUntil()).isEqualTo(ASSIGNED_AT);
        assertThat(result.getTier()).isEqualTo(silver);
        assertThat(result.getQualificationYear()).isEqualTo(2026);
        assertThat(customer.getTier()).isEqualTo(silver);
    }

    @Test
    void calculateEffectiveUntilWithoutGracePeriodIsStartOfNextYear() {
        properties.setGracePeriodMonths(0);

        assertThat(assignmentService.calculateYearlyQualificationEffectiveUntil(2026))
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
    }

    @Test
    void calculateEffectiveUntilAddsConfiguredGraceMonths() {
        properties.setGracePeriodMonths(3);

        assertThat(assignmentService.calculateYearlyQualificationEffectiveUntil(2026))
                .isEqualTo(LocalDateTime.of(2027, 4, 1, 0, 0));
    }

    @Test
    void resolveQualifiedTierTreatsNullTotalsAsZero() {
        when(tierRepository.findHighestQualifiedTier(BigDecimal.ZERO, 0)).thenReturn(Optional.of(member));

        assertThat(qualificationService.resolveQualifiedTier(null, null)).isEqualTo(member);
    }

    @Test
    void resolveQualifiedTierFailsWhenNoActiveTierConfigured() {
        when(tierRepository.findHighestQualifiedTier(any(), any())).thenReturn(Optional.empty());
        when(tierRepository.findLowestActiveTier()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualificationService.resolveQualifiedTier(BigDecimal.valueOf(500_000), 2))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.NO_ACTIVE_CUSTOMER_TIER)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private CustomerTier tier(String code, int minSpending, int minOrderCount) {
        CustomerTier tier = new CustomerTier();
        tier.setId(UUID.randomUUID());
        tier.setCode(code);
        tier.setName(code);
        tier.setMinSpending(BigDecimal.valueOf(minSpending));
        tier.setMinOrderCount(minOrderCount);
        return tier;
    }
}
