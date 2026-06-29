package org.hvtra.commonservice.outbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.commonservice.outbox.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseOutboxEvent extends BaseEntity {

    //Đây là loại object nghiệp vụ mà event đang nói tới.
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    //Đây là ID của object chính trong nghiệp vụ đó.
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private UUID aggregateId;

    //Đây là loại sự kiện đã xảy ra. vd: OrderCreated, OrderCancelled
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_key", nullable = false, columnDefinition = "Binary(16)")
    private UUID eventKey;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "headers", columnDefinition = "JSON")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "topic", length = 255)
    private String topic;

    @Column(name = "partition_no")
    private Integer partitionNo;

    @Column(name = "offset_no")
    private Long offsetNo;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = OutboxStatus.PENDING;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        if (maxRetry == null) {
            maxRetry = 5;
        }
    }


    public void initNew(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            UUID eventKey,
            String payload,
            String headers
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.payload = payload;
        this.headers = headers;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.maxRetry = 5;
    }

    public void markProcessing(String workerId) {
        this.status = OutboxStatus.PROCESSING;
        this.lockedBy = workerId;
        this.lockedAt = Instant.now();
        this.lastError = null;
    }

    public void markSent(String topic, Integer partitionNo, Long offsetNo) {
        this.status = OutboxStatus.SENT;
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.publishedAt = Instant.now();
        this.lockedBy = null;
        this.lockedAt = null;
        this.lastError = null;
    }

    public void markPublishFailed(String errorMessage) {
        this.retryCount = this.retryCount == null ? 1 : this.retryCount + 1;
        this.lastError = errorMessage;
        this.lockedBy = null;
        this.lockedAt = null;

        if (this.retryCount >= this.maxRetry) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
        }
    }
}