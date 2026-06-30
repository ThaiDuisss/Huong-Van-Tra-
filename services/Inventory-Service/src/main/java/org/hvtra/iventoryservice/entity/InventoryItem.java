package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

import java.util.UUID;

@Entity
@Table(
        name = "inventory_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reference_id", "item_type"})
)
@Getter
@Setter
public class InventoryItem extends BaseEntity {

    @Column(name = "reference_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private InventoryItemType itemType;

    /**
     * ID đơn vị lưu kho thực tế của nguyên liệu / biến thể tại kho này.
     * Ví dụ: lưu kho bằng Kg, Gram, Thùng...
     */
    @Column(name = "unit_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryStatus status = InventoryStatus.ACTIVE;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;
}
