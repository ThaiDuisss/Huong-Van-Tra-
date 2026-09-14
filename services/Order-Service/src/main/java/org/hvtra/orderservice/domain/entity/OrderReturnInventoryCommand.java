package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "order_return_inventory_commands",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_return_inventory_commands_command_id",
                        columnNames = "command_id"
                )
        },
        indexes = {
                @Index(name = "idx_order_return_inventory_commands_return", columnList = "return_id"),
                @Index(name = "idx_order_return_inventory_commands_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnInventoryCommand extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderReturn orderReturn;

    @Column(name = "command_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID commandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderReturnInventoryCommandStatus status;

    @Column(name = "requested_by", columnDefinition = "BINARY(16)")
    private UUID requestedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "inspection_id", columnDefinition = "BINARY(16)")
    private UUID inspectionId;

    @Builder.Default
    @OneToMany(mappedBy = "command", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderReturnInventoryCommandLine> lines = new ArrayList<>();

    public void addLine(OrderReturnInventoryCommandLine line) {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        lines.add(line);
        line.setCommand(this);
    }
}
