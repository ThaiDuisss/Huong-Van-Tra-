package org.hvtra.notificationservice.domain.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.kafka.inbox.BaseInbox;

@Entity
@Table(
        name = "inbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_inbox_event_id",
                        columnNames = {"event_id"}
                ),
                @UniqueConstraint(
                        name = "uk_notification_inbox_event_consumer",
                        columnNames = {"event_id", "consumer_group"}
                )
        },
        indexes = {
                @Index(name = "idx_notification_inbox_status", columnList = "status"),
                @Index(name = "idx_notification_inbox_topic_offset", columnList = "topic, partition_no, offset_no"),
                @Index(name = "idx_notification_inbox_saga_id", columnList = "saga_id")
        }
)
@AttributeOverride(name = "eventId", column = @Column(name = "event_id", nullable = false, length = 150))
@Getter
@Setter
@NoArgsConstructor
public class NotificationInbox extends BaseInbox {
}
