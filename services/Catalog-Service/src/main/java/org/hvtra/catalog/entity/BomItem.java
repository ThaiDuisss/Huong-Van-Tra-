package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "bom_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bom_items_variant_ingredient",
                columnNames = {"product_variant_id", "ingredient_id"}
        )
)
@Getter
@Setter
public class BomItem extends BaseEntity {

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false)
    private ProductVariant productVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Ingredient ingredient;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Unit unit;

    @Column(name = "waste_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal wasteRate;
}
