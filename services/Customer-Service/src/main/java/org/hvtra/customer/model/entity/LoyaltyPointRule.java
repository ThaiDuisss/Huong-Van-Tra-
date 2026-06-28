package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "loyalty_point_rules")
public class LoyaltyPointRule extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private CustomerTier tier;

    @Column(name = "spend_amount_per_block", nullable = false, precision = 15, scale = 2)
    private BigDecimal spendAmountPerBlock;

    @Column(name = "points_per_block", nullable = false)
    private Integer pointsPerBlock;

    @Column(name = "min_order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "earn_after_discount", nullable = false)
    private Boolean earnAfterDiscount = true;

    @Column(name = "allow_coupon_usage", nullable = false)
    private Boolean allowCouponUsage = true;

    @Column(name = "point_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal pointMultiplier = BigDecimal.ONE;

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LoyaltyPointRuleStatus status = LoyaltyPointRuleStatus.ACTIVE;
}
