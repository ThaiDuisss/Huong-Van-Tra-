package org.hvtra.promotionservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
import org.hvtra.promotionservice.enums.benefit.BenefitType;
import org.hvtra.promotionservice.enums.promotion.PromotionTargetType;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "promotion_targets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_promotion_target",
                        columnNames = {"promotion_id", "target_type", "target_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("java:S2160")
public class PromotionTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    PromotionTargetType targetType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "target_id", columnDefinition = "BINARY(16)", nullable = false)
    UUID targetId;

    @Builder.Default
    @Column(name = "is_excluded", nullable = false)
    Boolean excluded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_benefit_type", length = 50)
    BenefitType overrideBenefitType;

    @Column(name = "override_value", precision = 14, scale = 2)
    BigDecimal overrideValue;

    @Column(name = "override_max_discount", precision = 14, scale = 2)
    BigDecimal overrideMaxDiscount;

    @Column(name = "reason", columnDefinition = "TEXT")
    String reason;
}