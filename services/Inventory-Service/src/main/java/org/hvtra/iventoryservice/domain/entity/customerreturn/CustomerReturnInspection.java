package org.hvtra.iventoryservice.domain.entity.customerreturn;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "customer_return_inspections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_customer_return_inspection_code",
                columnNames = "inspection_code"
        ),
        indexes = {
                @Index(name = "idx_customer_return_inspections_return", columnList = "return_id"),
                @Index(name = "idx_customer_return_inspections_status", columnList = "status"),
                @Index(name = "idx_customer_return_inspections_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
public class CustomerReturnInspection extends BaseEntity {

    @Column(name = "inspection_code", nullable = false, unique = true, length = 100)
    private String inspectionCode;

    @Column(name = "return_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID returnId;

    @Column(name = "return_code", nullable = false, length = 50)
    private String returnCode;

    @Column(name = "command_id", unique = true, columnDefinition = "BINARY(16)")
    private UUID commandId;

    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "order_code", length = 50)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CustomerReturnInspectionStatus status = CustomerReturnInspectionStatus.CREATED;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerReturnInspectionLine> lines = new ArrayList<>();

    public void addLine(CustomerReturnInspectionLine line) {
        lines.add(line);
        line.setInspection(this);
    }
}
