package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.inbox.BaseInbox;

@Getter
@Entity
@Table(name = "inbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_inbox_events_message", columnNames = "message_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent extends BaseInbox {

}
