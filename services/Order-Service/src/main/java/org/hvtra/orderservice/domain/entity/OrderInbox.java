package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.kafka.inbox.BaseInbox;

@Entity
@Table(
        name = "inbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inbox_event_consumer",
                        columnNames = {"event_id", "consumer_group"}
                )
        },
        indexes = {
                @Index(name = "idx_inbox_saga_id", columnList = "saga_id"),
                @Index(name = "idx_inbox_status", columnList = "status"),
                @Index(name = "idx_inbox_topic_offset", columnList = "topic, partition_no, offset_no")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OrderInbox extends BaseInbox {
}
