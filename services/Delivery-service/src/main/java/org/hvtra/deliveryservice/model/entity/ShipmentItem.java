package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.AuditableEntity;

import java.math.BigDecimal;

import java.util.UUID;

@Getter
@Entity
@Table(name = "shipment_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_shipment_items_shipment"))
    private Shipment shipment;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "product_id", columnDefinition = "BINARY(16)")
    private UUID productId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "variant_id", columnDefinition = "BINARY(16)")
    private UUID variantId;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "unit_name", length = 50)
    private String unitName;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "weight_gram")
    private Integer weightGram;

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @Column(name = "note", length = 500)
    private String note;

    public static ShipmentItem create(
            UUID productId,
            UUID variantId,
            String sku,
            String productName,
            String unitName,
            BigDecimal quantity,
            Integer weightGram,
            boolean fragile,
            String note
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        ShipmentItem item = new ShipmentItem();
        item.productId = productId;
        item.variantId = variantId;
        item.sku = sku;
        item.productName = productName;
        item.unitName = unitName;
        item.quantity = quantity;
        item.weightGram = weightGram;
        item.fragile = fragile;
        item.note = note;
        return item;
    }

    void attachTo(Shipment shipment) {
        this.shipment = shipment;
    }
}
