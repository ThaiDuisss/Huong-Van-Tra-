package org.hvtra.catalog.outbox.polling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.nio.charset.StandardCharsets;

final class CatalogItemSyncRecordFactory {
    private final ObjectMapper mapper;
    private final ObjectWriter compactWriter;

    CatalogItemSyncRecordFactory(ObjectMapper mapper) {
        this.mapper = mapper;
        this.compactWriter = mapper.writer().without(SerializationFeature.INDENT_OUTPUT);
    }

    ProducerRecord<String, String> create(CatalogItemSyncEvent event) throws JsonProcessingException {
        JsonNode payload = mapper.readTree(event.payload());
        if (!CatalogItemSyncEvent.TOPIC.equals(event.topic()) || !CatalogItemSyncEvent.SOURCE.equals(event.sourceService())
                || !CatalogItemSyncEvent.supports(event.eventType()) || payload == null || !payload.isObject()
                || !event.eventId().equals(payload.path("eventId").asText())
                || !event.eventType().equals(payload.path("eventType").asText())
                || !event.aggregateId().equals(payload.path("referenceId").asText())
                || !event.messageKey().equals(event.aggregateId())) {
            throw new IllegalArgumentException("Invalid Catalog ITEM_SYNC outbox event");
        }

        RecordHeaders headers = new RecordHeaders();
        add(headers, "id", event.eventId());
        add(headers, "event_id", event.eventId());
        add(headers, "event_type", event.eventType());
        add(headers, "event_version", Integer.toString(event.eventVersion()));
        add(headers, "source_service", event.sourceService());
        add(headers, "saga_id", event.sagaId());
        add(headers, "correlation_id", event.correlationId());
        add(headers, "causation_id", event.causationId());
        add(headers, "aggregate_type", event.aggregateType());
        add(headers, "aggregate_id", event.aggregateId());
        add(headers, "business_key", event.businessKey());

        // The deployed connector wraps both strings with Kafka Connect JSON schemas.
        String key = compactWriter.writeValueAsString(envelope(event.messageKey(), false));
        String value = compactWriter.writeValueAsString(envelope(compactWriter.writeValueAsString(payload), true));
        return new ProducerRecord<>(event.topic(), null, event.occurredAt().toEpochMilli(), key, value, headers);
    }

    private ObjectNode envelope(String value, boolean jsonPayload) {
        ObjectNode envelope = mapper.createObjectNode();
        ObjectNode schema = envelope.putObject("schema").put("type", "string").put("optional", false);
        if (jsonPayload) {
            schema.put("name", "io.debezium.data.Json").put("version", 1);
        }
        return envelope.put("payload", value);
    }

    private void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
    }
}
