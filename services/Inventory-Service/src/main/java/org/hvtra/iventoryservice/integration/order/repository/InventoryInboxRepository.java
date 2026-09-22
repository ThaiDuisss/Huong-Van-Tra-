package org.hvtra.iventoryservice.integration.order.repository;

import jakarta.persistence.LockModeType;
import org.hvtra.commonservice.common_core.kafka.inbox.repository.BaseInboxRepository;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryInboxRepository extends BaseInboxRepository<InventoryInbox> {
    Optional<InventoryInbox> findByCommandIdAndConsumerGroup(
            String commandId,
            String consumerGroup);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select inbox
            from InventoryInbox inbox
            where inbox.commandId = :commandId
              and inbox.consumerGroup = :consumerGroup
            """)
    Optional<InventoryInbox> findByCommandIdAndConsumerGroupForUpdate(
            @Param("commandId") String commandId,
            @Param("consumerGroup") String consumerGroup);
}
