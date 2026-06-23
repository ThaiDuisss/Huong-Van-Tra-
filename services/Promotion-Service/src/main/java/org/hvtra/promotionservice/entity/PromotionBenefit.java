package org.hvtra.promotionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.promotionservice.enums.benefit.BenefitType;

import java.math.BigDecimal;

@Entity
@Table(name = "promotion_benefits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PromotionBenefit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 50)
    BenefitType benefitType;

    @Builder.Default
    @Column(name = "value", nullable = false, precision = 14, scale = 2)
    BigDecimal value = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 14, scale = 2)
    BigDecimal maxDiscount;
}