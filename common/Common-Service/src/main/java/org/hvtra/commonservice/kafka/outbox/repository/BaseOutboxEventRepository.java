package org.hvtra.commonservice.outbox.repository;

import org.hvtra.commonservice.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.outbox.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoRepositoryBean
public interface BaseOutboxEventRepository<T extends BaseOutboxEvent>
        extends JpaRepository<T, UUID> {

    @Query("""
            select e.id
            from #{#entityName} e
            where e.status = :status
            order by e.createdAt asc
            """)
    List<UUID> findIdsByStatus(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    @Query("""
            select e.id
            from #{#entityName} e
            where e.status = :status
              and e.lockedAt < :staleBefore
            order by e.createdAt asc
            """)
    List<UUID> findStaleProcessingIds(
            @Param("status") OutboxStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update #{#entityName} e
            set e.status = :processingStatus,
                e.lockedBy = :workerId,
                e.lockedAt = :lockedAt,
                e.lastError = null,
                e.updatedAt = :updatedAt,
                e.version = e.version + 1
            where e.id = :id
              and e.status = :pendingStatus
              and e.retryCount < e.maxRetry
            """)
    int claimPending(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("lockedAt") Instant lockedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update #{#entityName} e
            set e.status = :processingStatus,
                e.lockedBy = :workerId,
                e.lockedAt = :lockedAt,
                e.lastError = null,
                e.updatedAt = :updatedAt,
                e.version = e.version + 1
            where e.id = :id
              and e.status = :processingStatus
              and e.lockedAt < :staleBefore
              and e.retryCount < e.maxRetry
            """)
    int reclaimStale(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("lockedAt") Instant lockedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("staleBefore") Instant staleBefore,
            @Param("processingStatus") OutboxStatus processingStatus
    );


    @Query("""
            select e
            from #{#entityName} e
            where e.id = :id
            """)
    Optional<T> findMessageById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update #{#entityName} e
            set e.status = :sentStatus,
                e.topic = :topic,
                e.partitionNo = :partitionNo,
                e.offsetNo = :offsetNo,
                e.publishedAt = :publishedAt,
                e.lockedBy = null,
                e.lockedAt = null,
                e.lastError = null,
                e.updatedAt = :updatedAt,
                e.version = e.version + 1
            where e.id = :id
              and e.status = :processingStatus
              and e.lockedBy = :workerId
            """)
    int markSent(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("topic") String topic,
            @Param("partitionNo") Integer partitionNo,
            @Param("offsetNo") Long offsetNo,
            @Param("publishedAt") Instant publishedAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("sentStatus") OutboxStatus sentStatus
    );
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update #{#entityName} e
            set e.retryCount = e.retryCount + 1,
                e.status =
                    case
                        when e.retryCount + 1 >= e.maxRetry then :failedStatus
                        else :pendingStatus
                    end,
                e.lastError = :lastError,
                e.lockedBy = null,
                e.lockedAt = null,
                e.updatedAt = :updatedAt,
                e.version = e.version + 1
            where e.id = :id
              and e.status = :processingStatus
              and e.lockedBy = :workerId
            """)
    int markFailed(
            @Param("id") UUID id,
            @Param("workerId") String workerId,
            @Param("lastError") String lastError,
            @Param("updatedAt") Instant updatedAt,
            @Param("processingStatus") OutboxStatus processingStatus,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("failedStatus") OutboxStatus failedStatus
    );
}

