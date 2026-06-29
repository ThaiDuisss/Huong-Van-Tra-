package org.hvtra.commonservice.kafka.outbox.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.kafka.outbox.enums.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class OutboxEventFactory<T extends BaseOutboxEvent> {

    private final ObjectMapper objectMapper;
    @Value("${app.outbox.max-retry}")
    private Integer maxRetry;
    protected abstract T newEvent();

    public T create(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Object payload
    ) {
        return create(
                aggregateType,
                aggregateId,
                eventType,
                null,
                payload,
                null
        );
    }

    public T create(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            UUID eventKey,
            Object payload
    ) {
        return create(
                aggregateType,
                aggregateId,
                eventType,
                eventKey,
                payload,
                null
        );
    }

    public T create(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            UUID eventKey,
            Object payload,
            Object headers
    ) {
        try {
            Instant now = Instant.now();

            T event = newEvent();

            event.setId(UUID.randomUUID());
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setEventKey(eventKey);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setHeaders(headers == null ? null : objectMapper.writeValueAsString(headers));

            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(0);
            event.setMaxRetry(5);

            event.setCreatedAt(now);
            event.setUpdatedAt(now);

            return event;

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize outbox event payload", ex);
        }
    }
}