package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.enums.InventoryLocationType;
import org.hvtra.iventoryservice.enums.InventoryStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_locations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "code"})
)
@Getter
@Setter
public class InventoryLocation extends BaseEntity {

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_location_id")
    private InventoryLocation parentLocation;

    @OneToMany(mappedBy = "parentLocation", fetch = FetchType.LAZY)
    private List<InventoryLocation> childLocations = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryLocationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryStatus status = InventoryStatus.ACTIVE;
}
