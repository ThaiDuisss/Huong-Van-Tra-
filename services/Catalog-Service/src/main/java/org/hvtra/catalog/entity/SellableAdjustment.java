package org.hvtra.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sellable_adjustments", uniqueConstraints =
        @UniqueConstraint(name = "uq_sellable_adjustment_operation", columnNames = {"variant_id", "operation_key"}))
@Getter
@Setter
public class SellableAdjustment {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID variantId;

    @Column(name = "operation_key", nullable = false, length = 180)
    private String operationKey;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "old_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal oldQuantity;

    @Column(name = "new_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal newQuantity;

    @Column(name = "threshold_crossed", nullable = false)
    private boolean thresholdCrossed;
}
