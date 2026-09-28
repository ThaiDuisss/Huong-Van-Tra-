package org.hvtra.iventoryservice.domain.entity.movement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = "transaction_code")
)
@Getter
@Setter
public class InventoryTransaction extends BaseEntity {

    @Column(name = "transaction_code", nullable = false, unique = true, length = 100)
    private String transactionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private InventoryReferenceType referenceType;

    @Column(name = "reference_id", length = 50)
    private String referenceId;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_document_id")
    private InventoryDocument inventoryDocument;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryTransactionLine> lines = new ArrayList<>();

    public void addLine(InventoryTransactionLine line) {
        lines.add(line);
        line.setTransaction(this);
    }
}
