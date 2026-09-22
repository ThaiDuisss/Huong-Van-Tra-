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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.promotionservice.enums.benefit.BenefitType;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "promotion_benefits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("java:S2160")
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

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "gift_product_id", columnDefinition = "BINARY(16)")
    UUID giftProductId;

    @Column(name = "buy_quantity")
    Integer buyQuantity;

    @Column(name = "get_quantity")
    Integer getQuantity;

    @Column(name = "reason", columnDefinition = "TEXT")
    String reason;

    @Column(name = "min_margin_percent", precision = 5, scale = 2)
    BigDecimal minMarginPercent;

    @Column(name = "expected_margin_percent", precision = 5, scale = 2)
    BigDecimal expectedMarginPercent;

    @Column(name = "recommended_value", precision = 14, scale = 2)
    BigDecimal recommendedValue;

    @Column(name = "recommend_reason", columnDefinition = "TEXT")
    String recommendReason;

    @Column(name = "confidence_score", precision = 5, scale = 2)
    BigDecimal confidenceScore;

    @Builder.Default
    @Column(name = "ai_recommended", nullable = false)
    Boolean aiRecommended = false;
}