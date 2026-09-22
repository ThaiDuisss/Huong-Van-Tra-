package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.BarcodeType;

@Entity
@Table(name = "barcodes", uniqueConstraints = @UniqueConstraint(columnNames = "barcode_value"))
@Getter
@Setter
public class Barcode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private ProductVariant productVariant;

    @Column(name = "barcode_value", nullable = false, unique = true)
    private String barcodeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", nullable = false, length = 50)
    private BarcodeType barcodeType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
