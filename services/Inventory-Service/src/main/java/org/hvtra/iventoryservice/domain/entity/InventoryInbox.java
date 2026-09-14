package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
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
                ),
                @UniqueConstraint(
                        name = "uk_inbox_command_consumer",
                        columnNames = {"command_id", "consumer_group"}
                )
        },
        indexes = {
                @Index(name = "idx_inbox_command_id", columnList = "command_id"),
                @Index(name = "idx_inbox_saga_id", columnList = "saga_id"),
                @Index(name = "idx_inbox_status", columnList = "status"),
                @Index(name = "idx_inbox_topic_offset", columnList = "topic, partition_no, offset_no")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class InventoryInbox extends BaseInbox {
    @Column(name = "command_id", length = 36)
    private String commandId;

    @Column(name = "business_payload_hash", length = 64)
    private String businessPayloadHash;
}
