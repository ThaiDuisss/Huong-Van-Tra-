package org.hvtra.paymentservice.outbox.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.kafka.outbox.entity.BaseOutboxEvent;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_topic_created_at", columnList = "topic, created_at"),
                @Index(name = "idx_outbox_saga_id", columnList = "saga_id"),
                @Index(name = "idx_outbox_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_event_id",
                        columnNames = {"event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_outbox_business_key",
                        columnNames = {"event_type", "business_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent extends BaseOutboxEvent {
}
