package org.hvtra.customer.service;

import org.hvtra.customer.constant.Message;
import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.dto.response.TierProgressResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.mapper.CustomerMapper;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.model.enums.CustomerTierAssignmentStatus;
import org.hvtra.customer.model.enums.CustomerTierStatus;
import org.hvtra.customer.repository.CustomerPurchaseTransactionRepository;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierAssignmentRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.CustomerTierYearlyMetricRepository;
import org.hvtra.customer.service.impl.LoyaltyTierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoyaltyTierServiceTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 15, 10, 0);
    private static final LocalDateTime EFFECTIVE_UNTIL = LocalDateTime.of(2027, 2, 1, 0, 0);

    private CustomerRepository customerRepository;
    private CustomerTierRepository tierRepository;
    private CustomerPurchaseTransactionRepository transactionRepository;
    private CustomerTierYearlyMetricRepository metricRepository;
    private CustomerTierAssignmentRepository assignmentRepository;
    private TierQualificationService tierQualificationService;
    private TierAssignmentService tierAssignmentService;
    private LoyaltyTierServiceImpl loyaltyTierService;

    private CustomerTier member;
    private CustomerTier silver;
    private CustomerTier gold;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        tierRepository = mock(CustomerTierRepository.class);
        transactionRepository = mock(CustomerPurchaseTransactionRepository.class);
        metricRepository = mock(CustomerTierYearlyMetricRepository.class);
        assignmentRepository = mock(CustomerTierAssignmentRepository.class);
        tierQualificationService = mock(TierQualificationService.class);
        tierAssignmentService = mock(TierAssignmentService.class);
        loyaltyTierService = new LoyaltyTierServiceImpl(
                customerRepository,
                tierRepository,
                transactionRepository,
                metricRepository,
                assignmentRepository,
                tierQualificationService,
                tierAssignmentService,
                new CustomerMapper()
        );

        member = tier("MEMBER", 0, 0);
        silver = tier("SILVER", 1_000_000, 3);
        gold = tier("GOLD", 5_000_000, 10);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setTier(member);

        when(customerRepository.findByIdForUpdate(customer.getId())).thenReturn(Optional.of(customer));
        when(customerRepository.findByIdAndIsDeletedFalse(customer.getId())).thenReturn(Optional.of(customer));
        when(metricRepository.save(any(CustomerTierYearlyMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tierAssignmentService.calculateYearlyQualificationEffectiveUntil(anyInt())).thenReturn(EFFECTIVE_UNTIL);
    }

    @Test
    void recordPurchaseUpdatesMetricAndCustomerAndPromotesToQualifiedTier() {
        UUID orderId = UUID.randomUUID();
        when(tierQualificationService.resolveQualifiedTier(any(), any())).thenReturn(silver);

        loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                orderId, "DH-1", customer.getId(), BigDecimal.valueOf(1_200_000), OCCURRED_AT));

        ArgumentCaptor<CustomerPurchaseTransaction> transaction = ArgumentCaptor.forClass(CustomerPurchaseTransaction.class);
        verify(transactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getTransactionType()).isEqualTo(CustomerPurchaseTransactionType.PURCHASE);
        assertThat(transaction.getValue().getReferenceId()).isEqualTo(orderId);

        ArgumentCaptor<CustomerTierYearlyMetric> metric = ArgumentCaptor.forClass(CustomerTierYearlyMetric.class);
        verify(metricRepository, atLeastOnce()).save(metric.capture());
        CustomerTierYearlyMetric savedMetric = metric.getValue();
        assertThat(savedMetric.getTierYear()).isEqualTo(2026);
        assertThat(savedMetric.getTotalSpent()).isEqualByComparingTo("1200000");
        assertThat(savedMetric.getTotalOrderCount()).isEqualTo(1);
        assertThat(savedMetric.getQualifiedTier()).isEqualTo(silver);

        assertThat(customer.getTotalSpent()).isEqualByComparingTo("1200000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(1);
        assertThat(customer.getLastPurchaseDate()).isEqualTo(OCCURRED_AT);
        verify(tierAssignmentService).replaceActiveTier(customer, silver, 2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION, OCCURRED_AT, EFFECTIVE_UNTIL, "Yearly qualification");
        verify(customerRepository).save(customer);
    }

    @Test
    void recordPurchaseDoesNotDemoteWhenQualifiedTierIsLowerThanCurrent() {
        customer.setTier(gold);
        when(tierQualificationService.resolveQualifiedTier(any(), any())).thenReturn(member);

        loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                UUID.randomUUID(), "DH-1", customer.getId(), BigDecimal.valueOf(100_000), OCCURRED_AT));

        verify(tierAssignmentService, never()).replaceActiveTier(any(), any(), any(), any(), any(), any(), any());
        assertThat(customer.getTier()).isEqualTo(gold);
    }

    @Test
    void recordPurchaseIsIdempotentPerOrder() {
        UUID orderId = UUID.randomUUID();
        when(transactionRepository.existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType.PURCHASE, orderId))
                .thenReturn(true);

        loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                orderId, "DH-1", customer.getId(), BigDecimal.valueOf(100_000), OCCURRED_AT));

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(metricRepository, tierAssignmentService);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void recordPurchaseRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                UUID.randomUUID(), "DH-1", customer.getId(), BigDecimal.ZERO, OCCURRED_AT)))
                .isInstanceOf(CustomerServiceException.class)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void recordPurchaseRejectsEventWithoutCustomer() {
        assertThatThrownBy(() -> loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                UUID.randomUUID(), "DH-1", null, BigDecimal.TEN, OCCURRED_AT)))
                .isInstanceOf(CustomerServiceException.class);
    }

    @Test
    void recordPurchaseRejectsInactiveCustomer() {
        customer.setStatus(CustomerStatus.INACTIVE);

        assertThatThrownBy(() -> loyaltyTierService.recordPurchase(new OrderCompletedEvent(
                UUID.randomUUID(), "DH-1", customer.getId(), BigDecimal.TEN, OCCURRED_AT)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage("Customer is not active");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void partialRefundReducesSpentButKeepsOrderCount() {
        customer.setTotalSpent(BigDecimal.valueOf(500_000));
        customer.setTotalOrderCount(2);
        CustomerTierYearlyMetric metric = metric(500_000, 2);
        when(metricRepository.findByCustomerIdAndTierYearForUpdate(customer.getId(), 2026)).thenReturn(Optional.of(metric));
        when(tierQualificationService.resolveQualifiedTier(any(), any())).thenReturn(member);

        loyaltyTierService.recordPartialRefund(new OrderPartiallyRefundedEvent(
                UUID.randomUUID(), UUID.randomUUID(), customer.getId(), BigDecimal.valueOf(120_000), OCCURRED_AT, "Trả 1 món"));

        assertThat(metric.getTotalSpent()).isEqualByComparingTo("380000");
        assertThat(metric.getTotalOrderCount()).isEqualTo(2);
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("380000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(2);
        verify(customerRepository).save(customer);
    }

    @Test
    void fullRefundReducesSpentAndOrderCount() {
        customer.setTotalSpent(BigDecimal.valueOf(500_000));
        customer.setTotalOrderCount(2);
        CustomerTierYearlyMetric metric = metric(500_000, 2);
        when(metricRepository.findByCustomerIdAndTierYearForUpdate(customer.getId(), 2026)).thenReturn(Optional.of(metric));
        when(tierQualificationService.resolveQualifiedTier(any(), any())).thenReturn(member);

        loyaltyTierService.recordFullRefund(new OrderFullyRefundedEvent(
                UUID.randomUUID(), UUID.randomUUID(), customer.getId(), BigDecimal.valueOf(200_000), OCCURRED_AT, "Huỷ đơn"));

        assertThat(metric.getTotalSpent()).isEqualByComparingTo("300000");
        assertThat(metric.getTotalOrderCount()).isEqualTo(1);
        assertThat(customer.getTotalSpent()).isEqualByComparingTo("300000");
        assertThat(customer.getTotalOrderCount()).isEqualTo(1);
    }

    @Test
    void refundIsIdempotentPerRefundId() {
        UUID refundId = UUID.randomUUID();
        when(transactionRepository.existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType.FULL_REFUND, refundId))
                .thenReturn(true);

        loyaltyTierService.recordFullRefund(new OrderFullyRefundedEvent(
                UUID.randomUUID(), refundId, customer.getId(), BigDecimal.TEN, OCCURRED_AT, null));

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void refundRejectsMissingRefundId() {
        assertThatThrownBy(() -> loyaltyTierService.recordPartialRefund(new OrderPartiallyRefundedEvent(
                UUID.randomUUID(), null, customer.getId(), BigDecimal.TEN, OCCURRED_AT, null)))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage("Refund event is missing required data");
    }

    @Test
    void adjustTierRequiresReason() {
        assertThatThrownBy(() -> loyaltyTierService.adjustTier(customer.getId(),
                new ManualTierAdjustmentRequest(gold.getId(), "  ", null)))
                .isInstanceOf(CustomerServiceException.class)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(tierAssignmentService);
    }

    @Test
    void adjustTierReplacesActiveTierAsManualAdjustment() {
        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 9, 1, 0, 0);
        when(tierRepository.findByIdAndIsDeletedFalse(gold.getId())).thenReturn(Optional.of(gold));

        loyaltyTierService.adjustTier(customer.getId(),
                new ManualTierAdjustmentRequest(gold.getId(), "Khách VIP", effectiveFrom));

        verify(tierAssignmentService).replaceActiveTier(customer, gold, null,
                CustomerTierAssignmentReason.MANUAL_ADJUSTMENT, effectiveFrom, null, "Khách VIP");
    }

    @Test
    void getMetricReturnsEmptyMetricWhenCustomerHasNoPurchasesThatYear() {
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2025)).thenReturn(Optional.empty());

        CustomerTierYearlyMetric metric = loyaltyTierService.getMetric(customer.getId(), 2025);

        assertThat(metric.getCustomerId()).isEqualTo(customer.getId());
        assertThat(metric.getTierYear()).isEqualTo(2025);
        assertThat(metric.getTotalSpent()).isEqualByComparingTo("0");
        assertThat(metric.getTotalOrderCount()).isZero();
    }

    @Test
    void getProgressReportsRemainingRequirementsForNextTier() {
        CustomerTierYearlyMetric metric = metric(400_000, 1);
        metric.setTierYear(Year.now().getValue());
        metric.changeQualifiedTier(member);
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), Year.now().getValue())).thenReturn(Optional.of(metric));
        when(tierRepository.findByStatusOrderByMinSpendingAscMinOrderCountAsc(CustomerTierStatus.ACTIVE))
                .thenReturn(List.of(member, silver, gold));
        CustomerTierAssignment active = CustomerTierAssignment.active(customer, member, Year.now().getValue(),
                CustomerTierAssignmentReason.INITIAL_TIER, OCCURRED_AT, EFFECTIVE_UNTIL, null);
        when(assignmentRepository.findByCustomerIdAndStatus(customer.getId(), CustomerTierAssignmentStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        TierProgressResponse progress = loyaltyTierService.getProgress(customer.getId());

        assertThat(progress.currentTier().code()).isEqualTo("MEMBER");
        assertThat(progress.qualifiedTierForCurrentYear().code()).isEqualTo("MEMBER");
        assertThat(progress.nextTier().code()).isEqualTo("SILVER");
        assertThat(progress.remainingSpending()).isEqualByComparingTo("600000");
        assertThat(progress.remainingOrderCount()).isEqualTo(2);
        assertThat(progress.currentTierEffectiveUntil()).isEqualTo(EFFECTIVE_UNTIL);
    }

    @Test
    void getProgressHasNoNextTierAtHighestTier() {
        customer.setTier(gold);
        CustomerTierYearlyMetric metric = metric(9_000_000, 20);
        when(metricRepository.findByCustomerIdAndTierYear(eq(customer.getId()), any())).thenReturn(Optional.of(metric));
        when(tierQualificationService.resolveQualifiedTier(any(), any())).thenReturn(gold);
        when(tierRepository.findByStatusOrderByMinSpendingAscMinOrderCountAsc(CustomerTierStatus.ACTIVE))
                .thenReturn(List.of(member, silver, gold));
        when(assignmentRepository.findByCustomerIdAndStatus(any(), any())).thenReturn(Optional.empty());

        TierProgressResponse progress = loyaltyTierService.getProgress(customer.getId());

        assertThat(progress.qualifiedTierForCurrentYear().code()).isEqualTo("GOLD");
        assertThat(progress.nextTier()).isNull();
        assertThat(progress.remainingSpending()).isNull();
        assertThat(progress.remainingOrderCount()).isNull();
        assertThat(progress.currentTierEffectiveUntil()).isNull();
    }

    @Test
    void getCurrentYearMetricReturnsStoredMetricForCurrentYear() {
        CustomerTierYearlyMetric metric = metric(750_000, 2);
        metric.setTierYear(Year.now().getValue());
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), Year.now().getValue())).thenReturn(Optional.of(metric));

        assertThat(loyaltyTierService.getCurrentYearMetric(customer.getId())).isSameAs(metric);
    }

    @Test
    void getMetricRejectsUnknownCustomer() {
        UUID unknownId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        when(customerRepository.findByIdAndIsDeletedFalse(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyTierService.getCurrentYearMetric(unknownId))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.CUSTOMER_NOT_FOUND);
        verifyNoInteractions(metricRepository);
    }

    @Test
    void getPurchaseTransactionsReturnsCustomerPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        CustomerPurchaseTransaction purchase = CustomerPurchaseTransaction.purchase(
                customer.getId(), UUID.randomUUID(), BigDecimal.valueOf(100_000), OCCURRED_AT);
        when(transactionRepository.findByCustomerId(customer.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(purchase), pageable, 1));

        Page<CustomerPurchaseTransaction> page = loyaltyTierService.getPurchaseTransactions(customer.getId(), pageable);

        assertThat(page.getContent()).containsExactly(purchase);
    }

    @Test
    void getTierHistoryReturnsAssignmentsNewestFirst() {
        CustomerTierAssignment current = CustomerTierAssignment.active(customer, silver, 2026,
                CustomerTierAssignmentReason.YEARLY_QUALIFICATION, OCCURRED_AT, EFFECTIVE_UNTIL, null);
        CustomerTierAssignment initial = CustomerTierAssignment.active(customer, member, null,
                CustomerTierAssignmentReason.INITIAL_TIER, OCCURRED_AT.minusMonths(6), null, "Initial tier");
        when(assignmentRepository.findByCustomerIdOrderByEffectiveFromDesc(customer.getId()))
                .thenReturn(List.of(current, initial));

        assertThat(loyaltyTierService.getTierHistory(customer.getId())).containsExactly(current, initial);
    }

    @Test
    void getTierHistoryReturnsEmptyListForCustomerWithoutAssignments() {
        when(assignmentRepository.findByCustomerIdOrderByEffectiveFromDesc(customer.getId())).thenReturn(List.of());

        assertThat(loyaltyTierService.getTierHistory(customer.getId())).isEmpty();
    }

    @Test
    void getPurchaseTransactionsRejectsUnknownCustomer() {
        UUID unknownId = UUID.randomUUID();
        when(customerRepository.findByIdAndIsDeletedFalse(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyTierService.getPurchaseTransactions(unknownId, null))
                .isInstanceOf(CustomerServiceException.class);
        verifyNoInteractions(transactionRepository);
    }

    private CustomerTierYearlyMetric metric(int totalSpent, int totalOrderCount) {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(customer.getId(), 2026);
        metric.setTotalSpent(BigDecimal.valueOf(totalSpent));
        metric.setTotalOrderCount(totalOrderCount);
        return metric;
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
