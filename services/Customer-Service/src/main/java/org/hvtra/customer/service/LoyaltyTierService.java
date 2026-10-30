package org.hvtra.customer.service;

import org.hvtra.customer.dto.request.ManualTierAdjustmentRequest;
import org.hvtra.customer.dto.response.TierProgressResponse;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.hvtra.customer.integration.event.OrderFullyRefundedEvent;
import org.hvtra.customer.integration.event.OrderPartiallyRefundedEvent;
import org.hvtra.customer.model.entity.CustomerPurchaseTransaction;
import org.hvtra.customer.model.entity.CustomerTierAssignment;
import org.hvtra.customer.model.entity.CustomerTierYearlyMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface LoyaltyTierService {
    void recordPurchase(OrderCompletedEvent event);

    void recordPartialRefund(OrderPartiallyRefundedEvent event);

    void recordFullRefund(OrderFullyRefundedEvent event);

    CustomerTierAssignment adjustTier(UUID customerId, ManualTierAdjustmentRequest request);

    CustomerTierYearlyMetric getCurrentYearMetric(UUID customerId);

    CustomerTierYearlyMetric getMetric(UUID customerId, Integer year);

    Page<CustomerPurchaseTransaction> getPurchaseTransactions(UUID customerId, Pageable pageable);

    List<CustomerTierAssignment> getTierHistory(UUID customerId);

    TierProgressResponse getProgress(UUID customerId);
}
