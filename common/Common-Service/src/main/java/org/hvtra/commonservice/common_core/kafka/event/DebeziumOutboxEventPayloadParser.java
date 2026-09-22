package org.hvtra.commonservice.kafka.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.kafka.outbox.enums.DebeziumOp;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.BiConsumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class DebeziumOutboxEventPayloadParser {

    private final ObjectMapper objectMapper;

    /**
     * Backward-compatible parser: parse plain payload, Debezium envelope, or object with payload field.
     *
     * Note: for Kafka listeners consuming Debezium outbox topics, prefer {@link #parseCreateEvent}
     * so update/delete/snapshot events are skipped without throwing and without blocking offset commit.
     */
    public <T extends CommonEventPayload> T parse(
            String rawPayload,
            Class<T> payloadType,
            String eventName,
            BiConsumer<T, OutboxRowMetadata> metadataHydrator
    ) {
        return parseAnyEvent(rawPayload, payloadType, eventName, metadataHydrator);
    }

    /**
     * Parse only Debezium row events that represent an outbox row becoming visible.
     *
     * This is the safe method for CDC outbox consumers when the outbox table may also be updated
     * later, for example status/processed_at/retry_count updates. Those updates also contain
     * an `after` object, so checking only `after != null` is not enough.
     *
     * @return Optional.empty() for tombstones and Debezium op other than c/r. The listener can simply ack/return.
     */
    public <T extends CommonEventPayload> Optional<T> parseCreateEvent(
            String rawPayload,
            Class<T> payloadType,
            String eventName,
            BiConsumer<T, OutboxRowMetadata> metadataHydrator
    ) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.debug("Skip empty/tombstone {} payload", eventName);
            return Optional.empty();
        }

        try {
            JsonNode root = unwrapSchemaPayload(readRoot(rawPayload));
            if (root == null || root.isNull()) {
                log.debug("Skip null/tombstone {} payload", eventName);
                return Optional.empty();
            }
            String op = textValue(root, "op");

            // Plain payload or EventRouter output: no Debezium envelope, parse normally.
            // With JsonConverter schemas.enable=true, EventRouter messages arrive as:
            // {"schema": {...}, "payload": <business payload>}. unwrapSchemaPayload(...)
            // normalizes that to <business payload> before this branch.
            if (op == null && !root.has("before") && !root.has("after")) {
                return Optional.of(parsePayload(root, payloadType, metadataHydrator));
            }

            if (!DebeziumOp.isOutboxRowEvent(op)) {
                log.debug("Skip non-outbox-row Debezium {} event. op={}", eventName, op);
                return Optional.empty();
            }

            if (!root.hasNonNull("after")) {
                log.debug("Skip Debezium {} create event without after object", eventName);
                return Optional.empty();
            }

            return Optional.of(parsePayload(root, payloadType, metadataHydrator));
        } catch (JsonProcessingException ex) {
            log.error("Không parse được {} payload: {}", eventName, rawPayload, ex);
            throw new IllegalArgumentException("Invalid " + eventName + " payload", ex);
        }
    }

    public boolean isCreateEvent(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return false;
        }
        try {
            JsonNode root = unwrapSchemaPayload(readRoot(rawPayload));
            if (root == null || root.isNull()) {
                return false;
            }
            String op = textValue(root, "op");
            return op == null || DebeziumOp.isOutboxRowEvent(op);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid Debezium payload", ex);
        }
    }

    private <T extends CommonEventPayload> T parseAnyEvent(
            String rawPayload,
            Class<T> payloadType,
            String eventName,
            BiConsumer<T, OutboxRowMetadata> metadataHydrator
    ) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Empty " + eventName + " payload");
        }

        try {
            JsonNode root = unwrapSchemaPayload(readRoot(rawPayload));
            if (root == null || root.isNull()) {
                throw new IllegalArgumentException("Empty " + eventName + " payload");
            }
            return parsePayload(root, payloadType, metadataHydrator);
        } catch (JsonProcessingException ex) {
            log.error("Không parse được {} payload: {}", eventName, rawPayload, ex);
            throw new IllegalArgumentException("Invalid " + eventName + " payload", ex);
        }
    }

    private JsonNode readRoot(String rawPayload) throws JsonProcessingException {
        String payload = rawPayload.trim();
        if (payload.startsWith("\"")) {
            payload = objectMapper.readValue(payload, String.class);
        }
        return objectMapper.readTree(payload);
    }

    /**
     * Kafka Connect JsonConverter can wrap either Debezium envelope events or EventRouter
     * output in a top-level {schema, payload} object when schemas.enable=true. Business
     * code should not care about that transport envelope, so unwrap it before deciding
     * whether this is a Debezium row envelope or a routed outbox payload.
     */
    private JsonNode unwrapSchemaPayload(JsonNode root) throws JsonProcessingException {
        JsonNode current = root;

        while (current != null
                && current.has("schema")
                && current.has("payload")
                && current.size() <= 2) {
            current = current.get("payload");

            if (current != null && current.isTextual()) {
                String text = current.asText();
                if (!isBlank(text) && looksLikeJson(text)) {
                    current = objectMapper.readTree(text);
                }
            }
        }

        return current == null ? root : current;
    }

    private boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private <T extends CommonEventPayload> T parsePayload(
            JsonNode root,
            Class<T> payloadType,
            BiConsumer<T, OutboxRowMetadata> metadataHydrator
    ) throws JsonProcessingException {
        JsonNode payloadNode = root;
        JsonNode outboxRow = null;

        if (root.hasNonNull("after")) {
            outboxRow = root.get("after");
            payloadNode = outboxRow.hasNonNull("payload") ? outboxRow.get("payload") : outboxRow;
        } else if (root.hasNonNull("payload")) {
            payloadNode = root.get("payload");
        }

        T event = payloadNode.isTextual()
                ? objectMapper.readValue(payloadNode.asText(), payloadType)
                : objectMapper.treeToValue(payloadNode, payloadType);

        if (outboxRow != null) {
            hydrateCommonMetadata(event, outboxRow);
            if (metadataHydrator != null) {
                metadataHydrator.accept(event, toMetadata(outboxRow));
            }
        }

        return event;
    }

    private void hydrateCommonMetadata(CommonEventPayload event, JsonNode outboxRow) {
        if (isBlank(event.getEventId())) {
            event.setEventId(textValue(outboxRow, "event_id"));
        }
        if (isBlank(event.getEventType())) {
            event.setEventType(textValue(outboxRow, "event_type"));
        }
        if (isBlank(event.getEventKey())) {
            event.setEventKey(firstNonBlank(
                    textValue(outboxRow, "business_key"),
                    textValue(outboxRow, "message_key")
            ));
        }
    }

    private OutboxRowMetadata toMetadata(JsonNode outboxRow) {
        return new OutboxRowMetadata(
                textValue(outboxRow, "event_id"),
                textValue(outboxRow, "event_type"),
                firstNonBlank(textValue(outboxRow, "business_key"), textValue(outboxRow, "message_key")),
                textValue(outboxRow, "aggregate_type"),
                textValue(outboxRow, "aggregate_id"),
                textValue(outboxRow, "saga_id"),
                textValue(outboxRow, "correlation_id"),
                textValue(outboxRow, "causation_id"),
                textValue(outboxRow, "source_service"),
                textValue(outboxRow, "topic")
        );
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return isBlank(text) ? null : text;
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
