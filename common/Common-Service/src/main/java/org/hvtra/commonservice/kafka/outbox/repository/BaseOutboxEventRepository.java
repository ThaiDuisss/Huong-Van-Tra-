package org.hvtra.commonservice.kafka.outbox.repository;

import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface BaseOutboxEventRepository<T extends BaseOutboxEvent>
        extends JpaRepository<T, UUID> {

    boolean existsByEventTypeAndBusinessKey(String eventType, String businessKey);
}
