package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_outbox_status_locked_at", columnList = "status, locked_at"),
                @Index(name = "idx_outbox_event_type", columnList = "event_type"),
                @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_event_key_type",
                        columnNames = {"event_type", "event_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent extends BaseOutboxEvent {
}