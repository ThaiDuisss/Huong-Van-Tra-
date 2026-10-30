package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.config.LoyaltyTierProperties;
import org.hvtra.customer.constant.Message;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.CustomerTierYearlyMetricRepository;
import org.hvtra.customer.service.TierAssignmentService;
import org.hvtra.customer.service.TierQualificationService;
import org.hvtra.customer.service.YearlyTierReviewService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hvtra.customer.exception.CustomerServiceException.conflict;
import static org.hvtra.customer.exception.CustomerServiceException.notFound;

@Slf4j
@Service
@RequiredArgsConstructor
public class YearlyTierReviewServiceImpl implements YearlyTierReviewService {
    private final CustomerRepository customerRepository;
    private final CustomerTierRepository customerTierRepository;
    private final CustomerTierYearlyMetricRepository metricRepository;
    private final TierQualificationService tierQualificationService;
    private final TierAssignmentService tierAssignmentService;
    private final LoyaltyTierProperties properties;
    private final ObjectProvider<YearlyTierReviewService> selfProvider;

    @Override
    public void reviewCurrentYear() {
        int currentYear = Year.now().getValue();
        LocalDateTime reviewTime = LocalDate.of(currentYear, properties.getYearlyReview().getMonth(), properties.getYearlyReview().getDay()).atStartOfDay();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        int pageNumber = 0;
        Page<Customer> page;
        do {
            page = customerRepository.findByStatusAndIsDeletedFalse(CustomerStatus.ACTIVE, PageRequest.of(pageNumber, 100));
            page.forEach(customer -> {
                try {
                    selfProvider.getObject().reviewCustomerTier(customer.getId(), currentYear, reviewTime);
                    processed.incrementAndGet();
                } catch (RuntimeException ex) {
                    failed.incrementAndGet();
                    log.error("Failed yearly tier review for customerId={}", customer.getId(), ex);
                }
            });
            pageNumber++;
        } while (page.hasNext());
        log.info("Yearly tier review finished: processed={}, failed={}", processed.get(), failed.get());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reviewCustomerTier(UUID customerId, int currentYear, LocalDateTime reviewTime) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> notFound(Message.CUSTOMER_NOT_FOUND));
        CustomerTier qualifiedTier = metricRepository.findByCustomerIdAndTierYear(customerId, currentYear)
                .map(this::resolveMetricTier)
                .orElseGet(() -> customerTierRepository.findLowestActiveTier()
                        .orElseThrow(() -> conflict(Message.NO_ACTIVE_CUSTOMER_TIER)));

        if (customer.getTier() != null && Objects.equals(customer.getTier().getId(), qualifiedTier.getId())) {
            return;
        }

        tierAssignmentService.replaceActiveTier(
                customer,
                qualifiedTier,
                currentYear,
                CustomerTierAssignmentReason.YEARLY_REVIEW,
                reviewTime,
                null,
                "Yearly tier review after grace period"
        );
    }

    private CustomerTier resolveMetricTier(CustomerTierYearlyMetric metric) {
        if (metric.getQualifiedTier() != null) {
            return metric.getQualifiedTier();
        }
        return tierQualificationService.resolveQualifiedTier(metric.getTotalSpent(), metric.getTotalOrderCount());
    }
}
