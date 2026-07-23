package org.hvtra.promotionservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
public class PromotionInbox extends BaseInbox {
}
