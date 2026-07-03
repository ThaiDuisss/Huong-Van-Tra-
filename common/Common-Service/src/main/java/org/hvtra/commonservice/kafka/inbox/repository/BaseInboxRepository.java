package org.hvtra.commonservice.kafka.inbox.repository;

import org.hvtra.commonservice.kafka.inbox.BaseInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BaseInboxRepository<T extends BaseInbox>
        extends JpaRepository<T, UUID> {

    Optional<T> findByEventIdAndConsumerGroup(
            UUID eventId,
            String consumerGroup
    );

    boolean existsByEventIdAndConsumerGroup(
            UUID eventId,
            String consumerGroup
    );
}