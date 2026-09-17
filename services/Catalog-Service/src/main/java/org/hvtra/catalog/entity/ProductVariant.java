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

    /**
     * Định lượng của Mặt hàng, ví dụ 100, 250, 500.
     * Dùng để tự sinh variantName = "{Tên sản phẩm} {quantity} {Tên đơn vị}".
     */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "selling_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal costPrice;

    /**
     * Đơn vị bán thương mại của Mặt hàng này.
     * Ví dụ: "Hộp 100g", "Gói 250g", "Cốc".
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private Unit unit;

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

