package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants", uniqueConstraints = @UniqueConstraint(columnNames = "sku"))
@Getter
@Setter
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "selling_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "base_unit_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID baseUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_policy", nullable = false, length = 50)
    private InventoryPolicy inventoryPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @OneToMany(mappedBy = "productVariant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Barcode> barcodes = new ArrayList<>();

    public void addBarcode(Barcode barcode) {
        barcodes.add(barcode);
        barcode.setProductVariant(this);
    }
}
