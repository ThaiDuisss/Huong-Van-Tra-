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
import org.hvtra.commonservice.domain.entity.BaseEntity;
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
public class Promotion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    Event event;

    @Column(name = "name", nullable = false)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    PromotionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_method", nullable = false, length = 50)
    PromotionApplyMethod applyMethod;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    LocalDateTime endAt;

    @Builder.Default
    @Column(name = "is_stackable", nullable = false)
    Boolean stackable = false; // co duoc dung chung voi ma giam gia khac khong

    @Builder.Default
    @Column(name = "priority", nullable = false)
    Integer priority = 0; // do uu tien ap dung cai nao truoc

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    PromotionStatus status = PromotionStatus.ACTIVE;

    @Builder.Default
    @OneToMany(
            mappedBy = "promotion",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    List<PromotionCondition> conditions = new ArrayList<>();
    // 200000, VIP, ONLINE, ....

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
}