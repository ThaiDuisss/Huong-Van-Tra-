package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_approval_policies")
@Getter
@Setter
public class InventoryCountApprovalPolicy extends BaseEntity {

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "auto_approve_quantity_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal autoApproveQuantityLimit = BigDecimal.ZERO;

    @Column(name = "auto_approve_value_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal autoApproveValueLimit = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
