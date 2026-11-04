package org.hvtra.customer.service;

import org.hvtra.customer.config.LoyaltyTierProperties;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.CustomerTierYearlyMetricRepository;
import org.hvtra.customer.service.impl.YearlyTierReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YearlyTierReviewServiceTest {

    private static final LocalDateTime REVIEW_TIME = LocalDateTime.of(2026, 2, 1, 0, 0);

    private CustomerRepository customerRepository;
    private CustomerTierRepository tierRepository;
    private CustomerTierYearlyMetricRepository metricRepository;
    private TierQualificationService tierQualificationService;
    private TierAssignmentService tierAssignmentService;
    private YearlyTierReviewService self;
    private YearlyTierReviewServiceImpl reviewService;

    private CustomerTier member;
    private CustomerTier silver;
    private Customer customer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        tierRepository = mock(CustomerTierRepository.class);
        metricRepository = mock(CustomerTierYearlyMetricRepository.class);
        tierQualificationService = mock(TierQualificationService.class);
        tierAssignmentService = mock(TierAssignmentService.class);
        self = mock(YearlyTierReviewService.class);
        ObjectProvider<YearlyTierReviewService> selfProvider = mock(ObjectProvider.class);
        when(selfProvider.getObject()).thenReturn(self);
        reviewService = new YearlyTierReviewServiceImpl(customerRepository, tierRepository, metricRepository,
                tierQualificationService, tierAssignmentService, new LoyaltyTierProperties(), selfProvider);

        member = tier("MEMBER", 0);
        silver = tier("SILVER", 1_000_000);
        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setTier(silver);
        when(customerRepository.findByIdForUpdate(customer.getId())).thenReturn(Optional.of(customer));
    }

    @Test
    void reviewCustomerTierDowngradesToTierQualifiedInMetric() {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(customer.getId(), 2026);
        metric.changeQualifiedTier(member);
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2026)).thenReturn(Optional.of(metric));

        reviewService.reviewCustomerTier(customer.getId(), 2026, REVIEW_TIME);

        verify(tierAssignmentService).replaceActiveTier(customer, member, 2026,
                CustomerTierAssignmentReason.YEARLY_REVIEW, REVIEW_TIME, null, "Yearly tier review after grace period");
    }

    @Test
    void reviewCustomerTierResolvesTierFromMetricTotalsWhenNotYetQualified() {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(customer.getId(), 2026);
        metric.setTotalSpent(BigDecimal.valueOf(100_000));
        metric.setTotalOrderCount(1);
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2026)).thenReturn(Optional.of(metric));
        when(tierQualificationService.resolveQualifiedTier(BigDecimal.valueOf(100_000), 1)).thenReturn(member);

        reviewService.reviewCustomerTier(customer.getId(), 2026, REVIEW_TIME);

        verify(tierAssignmentService).replaceActiveTier(eq(customer), eq(member), eq(2026), any(), any(), any(), any());
    }

    @Test
    void reviewCustomerTierFallsBackToLowestTierWithoutMetric() {
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2026)).thenReturn(Optional.empty());
        when(tierRepository.findLowestActiveTier()).thenReturn(Optional.of(member));

        reviewService.reviewCustomerTier(customer.getId(), 2026, REVIEW_TIME);

        verify(tierAssignmentService).replaceActiveTier(eq(customer), eq(member), eq(2026), any(), any(), any(), any());
    }

    @Test
    void reviewCustomerTierSkipsCustomerAlreadyOnQualifiedTier() {
        CustomerTierYearlyMetric metric = CustomerTierYearlyMetric.create(customer.getId(), 2026);
        metric.changeQualifiedTier(silver);
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2026)).thenReturn(Optional.of(metric));

        reviewService.reviewCustomerTier(customer.getId(), 2026, REVIEW_TIME);

        verify(tierAssignmentService, never()).replaceActiveTier(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reviewCustomerTierFailsWhenNoActiveTierExists() {
        when(metricRepository.findByCustomerIdAndTierYear(customer.getId(), 2026)).thenReturn(Optional.empty());
        when(tierRepository.findLowestActiveTier()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.reviewCustomerTier(customer.getId(), 2026, REVIEW_TIME))
                .isInstanceOf(CustomerServiceException.class)
                .hasMessage(Message.NO_ACTIVE_CUSTOMER_TIER)
                .extracting(exception -> ((CustomerServiceException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reviewCurrentYearWalksAllPagesAndContinuesAfterFailure() {
        Customer first = customer();
        Customer failing = customer();
        Customer last = customer();
        when(customerRepository.findByStatusAndIsDeletedFalse(CustomerStatus.ACTIVE, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(first, failing), PageRequest.of(0, 100), 101));
        when(customerRepository.findByStatusAndIsDeletedFalse(CustomerStatus.ACTIVE, PageRequest.of(1, 100)))
                .thenReturn(new PageImpl<>(List.of(last), PageRequest.of(1, 100), 101));
        doThrow(new IllegalStateException("boom"))
                .when(self).reviewCustomerTier(eq(failing.getId()), anyInt(), any());

        reviewService.reviewCurrentYear();

        int year = Year.now().getValue();
        LocalDateTime expectedReviewTime = LocalDate.of(year, 2, 1).atStartOfDay();
        verify(self).reviewCustomerTier(first.getId(), year, expectedReviewTime);
        verify(self).reviewCustomerTier(failing.getId(), year, expectedReviewTime);
        verify(self).reviewCustomerTier(last.getId(), year, expectedReviewTime);
    }

    private Customer customer() {
        Customer value = new Customer();
        value.setId(UUID.randomUUID());
        return value;
    }

    private CustomerTier tier(String code, int minSpending) {
        CustomerTier tier = new CustomerTier();
        tier.setId(UUID.randomUUID());
        tier.setCode(code);
        tier.setName(code);
        tier.setMinSpending(BigDecimal.valueOf(minSpending));
        tier.setMinOrderCount(0);
        return tier;
    }
}
