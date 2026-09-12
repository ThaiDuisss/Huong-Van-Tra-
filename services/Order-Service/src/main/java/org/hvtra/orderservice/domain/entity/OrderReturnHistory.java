package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "order_return_history",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_return_history_action",
                        columnNames = {"return_id", "action"}
                )
        },
        indexes = {
                @Index(name = "idx_order_return_history_return", columnList = "return_id, occurred_at"),
                @Index(name = "idx_order_return_history_action", columnList = "action"),
                @Index(name = "idx_order_return_history_actor", columnList = "actor_user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReturnHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderReturn orderReturn;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private OrderReturnHistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private ReturnStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 50)
    private ReturnStatus toStatus;

    @Column(name = "actor_user_id", columnDefinition = "BINARY(16)")
    private UUID actorUserId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
