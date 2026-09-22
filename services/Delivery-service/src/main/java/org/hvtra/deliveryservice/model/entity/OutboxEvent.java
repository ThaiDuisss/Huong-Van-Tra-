package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.outbox.entity.BaseOutboxEvent;

@Getter
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseOutboxEvent {

}
