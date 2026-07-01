package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "customer_tier_yearly_metrics")
public class CustomerTierYearlyMetric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

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
}
