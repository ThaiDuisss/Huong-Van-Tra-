package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

@Entity
@Table(
        name = "inventory_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reference_id", "item_type"})
)
@Getter
@Setter
public class InventoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private InventoryItemType itemType;

    @Column(name = "base_unit_id", nullable = false)
    private Long baseUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryStatus status = InventoryStatus.ACTIVE;
}
