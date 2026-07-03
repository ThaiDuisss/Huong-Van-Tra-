package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.ManualPurchaseAdjustmentRequest;
import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LoyaltyTierService {
    void recordPurchase(OrderCompletedEvent event);

    void recordPartialRefund(OrderPartiallyRefundedEvent event);

    void recordFullRefund(OrderFullyRefundedEvent event);

    void recordManualAdjustment(ManualPurchaseAdjustmentRequest request);

    CustomerTierAssignment adjustTier(UUID customerId, ManualTierAdjustmentRequest request);

    CustomerTierYearlyMetric getCurrentYearMetric(UUID customerId);

    CustomerTierYearlyMetric getMetric(UUID customerId, Integer year);

    Page<CustomerPurchaseTransaction> getPurchaseTransactions(UUID customerId, Pageable pageable);
}
