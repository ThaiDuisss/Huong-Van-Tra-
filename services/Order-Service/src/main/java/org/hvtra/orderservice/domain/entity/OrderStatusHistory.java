package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.OrderStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private Order order;

    @Column(name = "from_status", length = 50)
    private OrderStatus fromStatus;

    @Column(name = "to_status", nullable = false, length = 50)
    private OrderStatus toStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by", columnDefinition = "BINARY(16)")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
