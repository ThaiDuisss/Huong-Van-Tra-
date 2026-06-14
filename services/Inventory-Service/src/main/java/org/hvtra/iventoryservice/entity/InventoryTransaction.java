package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inventory_transactions",
        uniqueConstraints = @UniqueConstraint(columnNames = "transaction_code")
)
@Getter
@Setter
public class InventoryTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_code", nullable = false, unique = true, length = 100)
    private String transactionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private InventoryReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryTransactionLine> lines = new ArrayList<>();

    public void addLine(InventoryTransactionLine line) {
        lines.add(line);
        line.setTransaction(this);
    }
}
