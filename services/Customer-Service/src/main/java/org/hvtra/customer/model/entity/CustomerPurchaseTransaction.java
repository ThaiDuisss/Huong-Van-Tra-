package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customer_purchase_transactions")
public class CustomerPurchaseTransaction extends BaseEntity {

    @Column(name = "customer_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID customerId;

    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    private UUID referenceId;

    @Column(name = "transaction_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerPurchaseTransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "order_count_delta", nullable = false)
    private Integer orderCountDelta = 0;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    public static CustomerPurchaseTransaction purchase(UUID customerId, UUID orderId, BigDecimal amount, LocalDateTime occurredAt) {
        requirePositive(amount, "Purchase amount must be greater than zero");
        requireNonNull(orderId, "Purchase orderId is required");
        return base(customerId, orderId, orderId, CustomerPurchaseTransactionType.PURCHASE, amount, 1, occurredAt, null);
    }

    public static CustomerPurchaseTransaction partialRefund(UUID customerId, UUID orderId, UUID refundId, BigDecimal refundAmount, LocalDateTime refundedAt, String reason) {
        requirePositive(refundAmount, "Refund amount must be greater than zero");
        requireNonNull(orderId, "Refund orderId is required");
        requireNonNull(refundId, "Refund referenceId is required");
        return base(customerId, orderId, refundId, CustomerPurchaseTransactionType.PARTIAL_REFUND, refundAmount.negate(), 0, refundedAt, reason);
    }

    public static CustomerPurchaseTransaction fullRefund(UUID customerId, UUID orderId, UUID refundId, BigDecimal refundAmount, LocalDateTime refundedAt, String reason) {
        requirePositive(refundAmount, "Refund amount must be greater than zero");
        requireNonNull(orderId, "Refund orderId is required");
        requireNonNull(refundId, "Refund referenceId is required");
        return base(customerId, orderId, refundId, CustomerPurchaseTransactionType.FULL_REFUND, refundAmount.negate(), -1, refundedAt, reason);
    }

    private static CustomerPurchaseTransaction base(UUID customerId, UUID orderId, UUID referenceId, CustomerPurchaseTransactionType type, BigDecimal amount, int orderCountDelta, LocalDateTime occurredAt, String reason) {
        requireNonNull(customerId, "customerId is required");
        requireNonNull(occurredAt, "occurredAt is required");
        CustomerPurchaseTransaction transaction = new CustomerPurchaseTransaction();
        transaction.setCustomerId(customerId);
        transaction.setOrderId(orderId);
        transaction.setReferenceId(referenceId);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setOrderCountDelta(orderCountDelta);
        transaction.setOccurredAt(occurredAt);
        transaction.setReason(reason);
        return transaction;
    }

    private static void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
