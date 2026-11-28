package org.hvtra.catalog.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.kafka.inbox.BaseInbox;

/** Event Kafka đã nhận để đảm bảo idempotency khi Catalog-Service xử lý bất đồng bộ. */
@Entity
@Table(
        name = "inbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inbox_event_consumer",
                columnNames = {"event_id", "consumer_group"}
        ),
        indexes = {
                @Index(name = "idx_inbox_status", columnList = "status"),
                @Index(name = "idx_inbox_topic_offset", columnList = "topic, partition_no, offset_no")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CatalogInbox extends BaseInbox {
}
