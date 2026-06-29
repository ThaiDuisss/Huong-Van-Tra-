package org.hvtra.promotionservice.entity.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.promotionservice.entity.Promotion;
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.coupon.CouponIssueReason;
import org.hvtra.promotionservice.enums.coupon.CouponScope;
import org.hvtra.promotionservice.enums.coupon.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("java:S2160")
public class Coupon extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30)
    CouponScope scope;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    UUID customerId;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_reason", length = 50)
    CouponIssueReason issueReason;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "source_order_id", columnDefinition = "BINARY(16)")
    UUID sourceOrderId;

    @Column(name = "issued_at")
    LocalDateTime issuedAt;

    @Column(name = "claimed_at")
    LocalDateTime claimedAt;

    @Builder.Default
    @Column(name = "usage_limit", nullable = false)
    Integer usageLimit = 1;

    @Builder.Default
    @Column(name = "used_count", nullable = false)
    Integer usedCount = 0;

    @Column(name = "expires_at")
    LocalDateTime expiresAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    CouponStatus status = CouponStatus.DRAFT;

    @Builder.Default
    @OneToMany(mappedBy = "coupon")
    List<CouponUsage> usages = new ArrayList<>();

    @Column(name = "snapshot_promotion_name", length = 255)
    String snapshotPromotionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_benefit_type", length = 50)
    BenefitType snapshotBenefitType;

    @Column(name = "snapshot_discount_value", precision = 14, scale = 2)
    BigDecimal snapshotDiscountValue;

    @Column(name = "snapshot_max_discount", precision = 14, scale = 2)
    BigDecimal snapshotMaxDiscount;
}