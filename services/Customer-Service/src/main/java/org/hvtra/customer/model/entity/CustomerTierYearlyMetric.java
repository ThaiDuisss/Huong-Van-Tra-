package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customer_tier_yearly_metrics")
public class CustomerTierYearlyMetric extends BaseEntity {

    @Column(name = "customer_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID customerId;

    @Column(name = "tier_year", nullable = false)
    private Integer tierYear;

    @Column(name = "total_spent", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "total_order_count", nullable = false)
    private Integer totalOrderCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qualified_tier_id")
    private CustomerTier qualifiedTier;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    public static CustomerTierYearlyMetric create(UUID customerId, Integer tierYear) {
        CustomerTierYearlyMetric metric = new CustomerTierYearlyMetric();
        metric.setCustomerId(customerId);
        metric.setTierYear(tierYear);
        metric.setTotalSpent(BigDecimal.ZERO);
        metric.setTotalOrderCount(0);
        metric.setCalculatedAt(LocalDateTime.now());
        return metric;
    }

    public void addPurchase(BigDecimal amount) {
        requirePositive(amount, "Purchase amount must be greater than zero");
        applyDelta(amount, 1);
    }

    public void applyRefund(BigDecimal amount, int orderCountDelta) {
        requireNegative(amount, "Refund amount must be negative");
        applyDelta(amount, orderCountDelta);
    }

    public void applyAdjustment(BigDecimal amount, int orderCountDelta) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Adjustment amount must not be zero");
        }
        applyDelta(amount, orderCountDelta);
    }

    public void changeQualifiedTier(CustomerTier qualifiedTier) {
        this.qualifiedTier = qualifiedTier;
        this.calculatedAt = LocalDateTime.now();
    }

    private void applyDelta(BigDecimal amount, int orderCountDelta) {
        this.totalSpent = nonNegative(defaultMoney(this.totalSpent).add(amount));
        this.totalOrderCount = Math.max(defaultCount(this.totalOrderCount) + orderCountDelta, 0);
        this.calculatedAt = LocalDateTime.now();
    }

    private static void requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNegative(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int defaultCount(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
