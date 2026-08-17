package org.hvtra.deliveryservice.repository;

import org.hvtra.commonservice.kafka.inbox.repository.BaseInboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import org.hvtra.deliveryservice.model.entity.InboxEvent;
import java.util.Optional;
import java.util.UUID;

public interface InboxEventRepository extends BaseInboxRepository<InboxEvent> {

}
