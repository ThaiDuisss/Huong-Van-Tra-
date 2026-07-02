package org.hvtra.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.catalog.enums.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "boms",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_variant_id", "version"}),
                @UniqueConstraint(name = "uq_boms_bom_code", columnNames = {"bom_code"})
        }
)
@AttributeOverrides({
        @AttributeOverride(name = "version", column = @Column(name = "version_audit"))
})
@Getter
@Setter
public class Bom extends BaseEntity {

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "name")
    private String name;

    @Column(name = "bom_code", unique = true, length = 100)
    private String bomCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "description", length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", insertable = false, updatable = false)
    private ProductVariant productVariant;

    @Column(name = "version", nullable = false)
    private Integer bomVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.INACTIVE;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BomItem> items = new ArrayList<>();

    public void addItem(BomItem item) {
        items.add(item);
        item.setBom(this);
    }
}
