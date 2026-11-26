package org.hvtra.iventoryservice.outbox.polling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class InventoryOutboxRecordFactory {
    private static final int MAX_WIRE_BYTES = 1024 * 1024;
    private final ObjectReader reader;

    InventoryOutboxRecordFactory(ObjectMapper mapper) {
        this.reader = mapper.reader().with(
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS
        );
    }

    ProducerRecord<String, String> create(PollingInventoryEvent event) throws RejectedEvent {
        ObjectNode body = parse(event.payload());
        if (!PollingInventoryEvent.supports(event.eventType(), event.topic())
                || !uuid(event.eventId())
                || !event.eventId().equals(body.path("eventId").textValue())
                || !event.eventType().equals(body.path("eventType").textValue())
                || event.eventVersion() != 1
                || !"inventory-service".equals(event.source())
                || !uuid(event.aggregateId())
                || event.key() == null
                || event.key().isBlank()) {
            throw new RejectedEvent("INVALID_CONTRACT_METADATA");
        }

        RecordHeaders headers = new RecordHeaders();
        header(headers, "event_id", event.eventId());
        header(headers, "event_type", event.eventType());
        header(headers, "event_version", "1");
        header(headers, "source_service", event.source());
        header(headers, "aggregate_type", event.aggregateType());
        header(headers, "aggregate_id", event.aggregateId());
        header(headers, "business_key", event.businessKey());
        if (event.sagaId() != null) {
            header(headers, "saga_id", event.sagaId());
        }
        if (event.correlationId() != null) {
            header(headers, "correlation_id", event.correlationId());
        }
        if (event.causationId() != null) {
            header(headers, "causation_id", event.causationId());
        }

        return new ProducerRecord<>(
                event.topic(),
                null,
                event.occurredAt().toEpochMilli(),
                event.key(),
                event.payload(),
                headers
        );
    }

    private ObjectNode parse(String payload) throws RejectedEvent {
        if (payload != null && payload.getBytes(StandardCharsets.UTF_8).length <= MAX_WIRE_BYTES) {
            try {
                if (reader.readTree(payload) instanceof ObjectNode body) {
                    return body;
                }
            } catch (JsonProcessingException ignored) {
                // Error code stored on publication row.
            }
        }
        throw new RejectedEvent("MALFORMED_PAYLOAD");
    }

    private static boolean uuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void header(RecordHeaders headers, String key, String value) {
        headers.add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    static final class RejectedEvent extends Exception {
        RejectedEvent(String code) {
            super(code);
        }
    }
}
