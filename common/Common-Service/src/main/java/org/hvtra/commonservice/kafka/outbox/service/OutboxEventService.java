package org.hvtra.commonservice.kafka.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.entity.BaseOutboxEvent;
import org.hvtra.commonservice.kafka.outbox.factory.OutboxEventFactory;
import org.hvtra.commonservice.kafka.outbox.repository.BaseOutboxEventRepository;

import java.util.UUID;

@RequiredArgsConstructor
public class OutboxEventService<T extends BaseOutboxEvent> {

    private final BaseOutboxEventRepository<T> outboxEventRepository;
    private final OutboxEventFactory<T> outboxEventFactory;
    private final ObjectMapper objectMapper;

    public void saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String eventKey,
            Object payload
    ) {
        saveEvent(aggregateType, UUID.fromString(aggregateId), eventType, UUID.fromString(eventKey), payload, null);
    }

    public void saveEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            UUID eventKey,
            Object payload,
            Object headers
    ) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = headers == null ? null : objectMapper.writeValueAsString(headers);

            T event = outboxEventFactory.create(
                    aggregateType,
                    aggregateId,
                    eventType,
                    eventKey,
                    payloadJson,
                    headersJson
            );

            outboxEventRepository.save(event);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize outbox event", ex);
        }
    }

    public void saveEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            UUID eventKey,
            Object payload
    ) {
        saveEvent(
                aggregateType,
                aggregateId.toString(),
                eventType,
                eventKey.toString(),
                payload
        );
    }
}