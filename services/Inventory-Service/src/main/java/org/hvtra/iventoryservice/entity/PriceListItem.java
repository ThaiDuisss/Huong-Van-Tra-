package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "price_list_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"price_list_id", "product_variant_id"}))
@Getter
@Setter
public class PriceListItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "selling_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal sellingPrice;
}
