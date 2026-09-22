package org.hvtra.promotionservice.entity;

import jakarta.persistence.CascadeType;
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
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.promotionservice.entity.coupon.Coupon;
import org.hvtra.promotionservice.enums.promotion.PromotionApplyMethod;
import org.hvtra.promotionservice.enums.promotion.PromotionStatus;
import org.hvtra.promotionservice.enums.promotion.PromotionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("java:S2160")
public class Promotion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    Event event;

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "objective", columnDefinition = "TEXT")
    String objective;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    PromotionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_method", nullable = false, length = 50)
    PromotionApplyMethod applyMethod;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at")
    LocalDateTime endAt;

    @Builder.Default
    @Column(name = "is_stackable", nullable = false)
    Boolean stackable = false;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    Integer priority = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    PromotionStatus status = PromotionStatus.DRAFT;

    @Builder.Default
    @OneToMany(
            mappedBy = "promotion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    List<PromotionTarget> targets = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "promotion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    List<PromotionCondition> conditions = new ArrayList<>();

    @Builder.Default
    @OneToMany(
            mappedBy = "promotion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    List<PromotionBenefit> benefits = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "promotion")
    List<Coupon> coupons = new ArrayList<>();

    public void addTarget(PromotionTarget target) {
        targets.add(target);
        target.setPromotion(this);
    }

    public void addCondition(PromotionCondition condition) {
        conditions.add(condition);
        condition.setPromotion(this);
    }

    public void addBenefit(PromotionBenefit benefit) {
        benefits.add(benefit);
        benefit.setPromotion(this);
    }

    public void addCoupon(Coupon coupon) {
        coupons.add(coupon);
        coupon.setPromotion(this);
    }
}