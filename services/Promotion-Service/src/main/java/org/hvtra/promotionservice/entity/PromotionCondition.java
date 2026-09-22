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
import org.hvtra.promotionservice.enums.condition.ConditionOperator;
import org.hvtra.promotionservice.enums.condition.ConditionType;

@Entity
@Table(name = "promotion_conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("java:S2160")
public class PromotionCondition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 50)
    ConditionType conditionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    ConditionOperator operator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_value", nullable = false, columnDefinition = "JSON")
    String conditionValue;
}