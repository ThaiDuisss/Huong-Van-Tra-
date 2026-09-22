package org.hvtra.commonservice.common_core.kafka.outbox.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.entity.BaseOutboxEvent;

import java.time.Instant;

@RequiredArgsConstructor
public abstract class OutboxEventFactory<T extends BaseOutboxEvent> {

    private final ObjectMapper objectMapper;

    protected abstract T newEvent();

    public T create(
            OutboxSaveCommand command,
            String eventId,
            Instant occurredAt
    ) {
        try {
            Instant now = Instant.now();

            T event = newEvent();

            event.setTopic(command.topic());
            event.setSourceService(command.sourceService());
            event.setAggregateType(command.aggregateType());
            event.setAggregateId(command.aggregateId());
            event.setEventType(command.eventType());
            event.setEventId(eventId);
            event.setMessageKey(command.messageKey());
            event.setBusinessKey(command.businessKey());
            event.setSagaId(command.sagaId());
            event.setCorrelationId(command.correlationId());
            event.setCausationId(command.causationId());
            event.setPayload(objectMapper.writeValueAsString(command.payload()));
            event.setHeaders(command.headers() == null ? null : objectMapper.writeValueAsString(command.headers()));
            event.setOccurredAt(occurredAt);

            event.setCreatedAt(now);
            event.setUpdatedAt(now);

            return event;

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize outbox event payload", ex);
        }
    }
}
