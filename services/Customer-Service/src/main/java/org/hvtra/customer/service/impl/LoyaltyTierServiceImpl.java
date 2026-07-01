package org.hvtra.customer.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.customer.dto.request.ManualPurchaseAdjustmentRequest;
import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.exception.CustomerNotFoundException;
import org.hvtra.customer.exception.CustomerTierNotFoundException;
import org.hvtra.customer.exception.InvalidPurchaseTransactionException;
import org.hvtra.customer.exception.InvalidTierAdjustmentException;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTier;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.model.enums.CustomerTierAssignmentReason;
import org.hvtra.customer.repository.CustomerPurchaseTransactionRepository;
import org.hvtra.customer.repository.CustomerRepository;
import org.hvtra.customer.repository.CustomerTierRepository;
import org.hvtra.customer.repository.CustomerTierYearlyMetricRepository;
import org.hvtra.customer.service.LoyaltyTierService;
import org.hvtra.customer.service.TierAssignmentService;
import org.hvtra.customer.service.TierQualificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoyaltyTierServiceImpl implements LoyaltyTierService {
    private final CustomerRepository customerRepository;
    private final CustomerTierRepository customerTierRepository;
    private final CustomerPurchaseTransactionRepository transactionRepository;
    private final CustomerTierYearlyMetricRepository metricRepository;
    private final TierQualificationService tierQualificationService;
    private final TierAssignmentService tierAssignmentService;

    @Override
    @Transactional
    public void recordPurchase(OrderCompletedEvent event) {
        validatePurchase(event);
        if (transactionRepository.existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType.PURCHASE, event.orderId())) {
            return;
        }
        Customer customer = findActiveCustomerForUpdate(event.customerId());
        CustomerPurchaseTransaction transaction = CustomerPurchaseTransaction.purchase(customer.getId(), event.orderId(), event.finalAmount(), event.occurredAt());
        transactionRepository.save(transaction);

        CustomerTierYearlyMetric metric = findOrCreateMetricForUpdate(customer.getId(), event.occurredAt().getYear());
        metric.addPurchase(event.finalAmount());
        CustomerTier qualifiedTier = tierQualificationService.resolveQualifiedTier(metric.getTotalSpent(), metric.getTotalOrderCount());
        metric.changeQualifiedTier(qualifiedTier);

        customer.addPurchase(event.finalAmount(), event.occurredAt());
        maybePromote(customer, qualifiedTier, event.occurredAt().getYear(), event.occurredAt());

        metricRepository.save(metric);
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void recordPartialRefund(OrderPartiallyRefundedEvent event) {
        validateRefund(event.customerId(), event.orderId(), event.refundId(), event.refundAmount(), event.refundedAt());
        if (transactionRepository.existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType.PARTIAL_REFUND, event.refundId())) {
            return;
        }
        Customer customer = findActiveCustomerForUpdate(event.customerId());
        CustomerPurchaseTransaction transaction = CustomerPurchaseTransaction.partialRefund(customer.getId(), event.orderId(), event.refundId(), event.refundAmount(), event.refundedAt(), event.reason());
        transactionRepository.save(transaction);
        applyRefund(customer, event.refundedAt(), event.refundAmount().negate(), 0);
    }

    @Override
    @Transactional
    public void recordFullRefund(OrderFullyRefundedEvent event) {
        validateRefund(event.customerId(), event.orderId(), event.refundId(), event.refundAmount(), event.refundedAt());
        if (transactionRepository.existsByTransactionTypeAndReferenceId(CustomerPurchaseTransactionType.FULL_REFUND, event.refundId())) {
            return;
        }
        Customer customer = findActiveCustomerForUpdate(event.customerId());
        CustomerPurchaseTransaction transaction = CustomerPurchaseTransaction.fullRefund(customer.getId(), event.orderId(), event.refundId(), event.refundAmount(), event.refundedAt(), event.reason());
        transactionRepository.save(transaction);
        applyRefund(customer, event.refundedAt(), event.refundAmount().negate(), -1);
    }

    @Override
    @Transactional
    public void recordManualAdjustment(ManualPurchaseAdjustmentRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidPurchaseTransactionException("Adjustment amount must not be zero");
        }
        LocalDateTime occurredAt = request.occurredAt() == null ? LocalDateTime.now() : request.occurredAt();
        Customer customer = findActiveCustomerForUpdate(request.customerId());
        CustomerPurchaseTransaction transaction = CustomerPurchaseTransaction.adjustment(customer.getId(), request.amount(), request.orderCountDelta(), occurredAt, request.reason());
        transactionRepository.save(transaction);

        CustomerTierYearlyMetric metric = findOrCreateMetricForUpdate(customer.getId(), occurredAt.getYear());
        metric.applyAdjustment(request.amount(), request.orderCountDelta() == null ? 0 : request.orderCountDelta());
        CustomerTier qualifiedTier = tierQualificationService.resolveQualifiedTier(metric.getTotalSpent(), metric.getTotalOrderCount());
        metric.changeQualifiedTier(qualifiedTier);
        customer.applyPurchaseDelta(request.amount(), request.orderCountDelta() == null ? 0 : request.orderCountDelta());
        maybePromote(customer, qualifiedTier, occurredAt.getYear(), occurredAt);
        metricRepository.save(metric);
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public CustomerTierAssignment adjustTier(UUID customerId, ManualTierAdjustmentRequest request) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidTierAdjustmentException("Manual tier adjustment reason is required");
        }
        Customer customer = findActiveCustomerForUpdate(customerId);
        CustomerTier tier = customerTierRepository.findByIdAndIsDeletedFalse(request.tierId())
                .orElseThrow(() -> new CustomerTierNotFoundException("Customer tier not found"));
        LocalDateTime effectiveFrom = request.effectiveFrom() == null ? LocalDateTime.now() : request.effectiveFrom();
        return tierAssignmentService.replaceActiveTier(customer, tier, null, CustomerTierAssignmentReason.MANUAL_ADJUSTMENT, effectiveFrom, null, request.reason());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerTierYearlyMetric getCurrentYearMetric(UUID customerId) {
        return getMetric(customerId, Year.now().getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerTierYearlyMetric getMetric(UUID customerId, Integer year) {
        customerRepository.findByIdAndIsDeletedFalse(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        return metricRepository.findByCustomerIdAndTierYear(customerId, year)
                .orElseGet(() -> CustomerTierYearlyMetric.create(customerId, year));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerPurchaseTransaction> getPurchaseTransactions(UUID customerId, Pageable pageable) {
        customerRepository.findByIdAndIsDeletedFalse(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        return transactionRepository.findByCustomerId(customerId, pageable);
    }

    private void applyRefund(Customer customer, LocalDateTime refundedAt, BigDecimal negativeAmount, int orderCountDelta) {
        CustomerTierYearlyMetric metric = findOrCreateMetricForUpdate(customer.getId(), refundedAt.getYear());
        metric.applyRefund(negativeAmount, orderCountDelta);
        CustomerTier qualifiedTier = tierQualificationService.resolveQualifiedTier(metric.getTotalSpent(), metric.getTotalOrderCount());
        metric.changeQualifiedTier(qualifiedTier);
        customer.applyPurchaseDelta(negativeAmount, orderCountDelta);
        metricRepository.save(metric);
        customerRepository.save(customer);
    }

    private void maybePromote(Customer customer, CustomerTier qualifiedTier, int qualificationYear, LocalDateTime effectiveFrom) {
        if (isHigherTier(qualifiedTier, customer.getTier())) {
            tierAssignmentService.replaceActiveTier(
                    customer,
                    qualifiedTier,
                    qualificationYear,
                    CustomerTierAssignmentReason.YEARLY_QUALIFICATION,
                    effectiveFrom,
                    tierAssignmentService.calculateYearlyQualificationEffectiveUntil(qualificationYear),
                    "Yearly qualification"
            );
        }
    }

    private boolean isHigherTier(CustomerTier candidate, CustomerTier current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        int spendingCompare = candidate.getMinSpending().compareTo(current.getMinSpending());
        if (spendingCompare != 0) {
            return spendingCompare > 0;
        }
        return candidate.getMinOrderCount() > current.getMinOrderCount();
    }

    private CustomerTierYearlyMetric findOrCreateMetricForUpdate(UUID customerId, int year) {
        return metricRepository.findByCustomerIdAndTierYearForUpdate(customerId, year)
                .orElseGet(() -> metricRepository.save(CustomerTierYearlyMetric.create(customerId, year)));
    }

    private Customer findActiveCustomerForUpdate(UUID customerId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new InvalidPurchaseTransactionException("Customer is not active");
        }
        return customer;
    }

    private void validatePurchase(OrderCompletedEvent event) {
        if (event == null || event.orderId() == null || event.customerId() == null || event.occurredAt() == null) {
            throw new InvalidPurchaseTransactionException("Purchase event is missing required data");
        }
        if (event.finalAmount() == null || event.finalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPurchaseTransactionException("Purchase amount must be greater than zero");
        }
    }

    private void validateRefund(UUID customerId, UUID orderId, UUID refundId, BigDecimal refundAmount, LocalDateTime refundedAt) {
        if (customerId == null || orderId == null || refundId == null || refundedAt == null) {
            throw new InvalidPurchaseTransactionException("Refund event is missing required data");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPurchaseTransactionException("Refund amount must be greater than zero");
        }
    }
}
