package org.hvtra.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.customer.model.enums.CustomerTierStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "customer_tiers")
public class CustomerTier extends CustomerBaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "min_spending", nullable = false, precision = 15, scale = 2)
    private BigDecimal minSpending = BigDecimal.ZERO;

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CustomerTierStatus status = CustomerTierStatus.ACTIVE;
}
