package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.customer.model.enums.CustomerTierStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "customer_tiers")
public class CustomerTier extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "min_spending", nullable = false, precision = 15, scale = 2)
    private BigDecimal minSpending = BigDecimal.ZERO;

    @Column(name = "min_order_count", nullable = false)
    private Integer minOrderCount = 0;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerTierStatus status = CustomerTierStatus.ACTIVE;

    @Size(max = 7)
    @ColumnDefault("'#6B7280'")
    @Column(name = "color_code", nullable = false, length = 7)
    private String colorCode;
}
