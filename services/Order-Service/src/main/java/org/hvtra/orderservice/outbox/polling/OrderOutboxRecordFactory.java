package org.hvtra.orderservice.outbox.polling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.hvtra.orderservice.outbox.event.OrderEventType;
import org.hvtra.orderservice.outbox.service.OrderKafkaSigner;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class OrderOutboxRecordFactory {
    private static final int MAX_WIRE_BYTES = 1024 * 1024;
    private final ObjectReader reader;
    private final OrderKafkaSigner signer;

    OrderOutboxRecordFactory(ObjectMapper mapper, OrderKafkaSigner signer) {
        this.reader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.signer = signer;
    }

    ProducerRecord<String, String> create(PollingOrderEvent event) throws RejectedEvent {
        ObjectNode body = parse(event.payload());
        boolean purchase = OrderEventType.ORDER_COMPLETED.equals(event.eventType());
        String key = body.path(purchase ? "orderId" : "returnId").textValue();
        String aggregate = purchase ? "ORDER" : "ORDER_RETURN";
        String businessKey = key + (purchase ? ":order-completed" : ":return-completed");
        if (!PollingOrderEvent.supports(event.eventType(), event.topic())
                || !uuid(event.eventId()) || !matching(body, "eventId", event.eventId())
                || !matching(body, "eventType", event.eventType())
                || !body.path("eventVersion").isIntegralNumber()
                || !body.path("eventVersion").canConvertToInt()
                || body.path("eventVersion").intValue() != 1 || event.eventVersion() != 1
                || !"order-service".equals(event.source()) || !matching(body, "sourceService", event.source())
                || !uuid(body.path("orderId").textValue()) || !uuid(key)
                || !key.equals(event.key()) || !matching(body, "eventKey", key)
                || !key.equals(event.aggregateId()) || !aggregate.equals(event.aggregateType())
                || !businessKey.equals(event.businessKey())
                || !body.has("customerId") || (!body.get("customerId").isNull()
                    && !uuid(body.get("customerId").textValue()))) {
            throw new RejectedEvent("INVALID_CONTRACT_METADATA");
        }

        switch (signer.verify(event.topic(), event.key(), body)) {
            case INVALID -> throw new RejectedEvent("INVALID_OR_MISSING_HMAC");
            case KEY_UNAVAILABLE -> throw new RejectedEvent("SIGNING_KEY_UNAVAILABLE", true);
            case VALID -> { }
        }
        validateBusinessPayload(body, purchase);

        RecordHeaders headers = new RecordHeaders();
        header(headers, "event_id", event.eventId());
        header(headers, "event_type", event.eventType());
        header(headers, "event_version", "1");
        header(headers, "source_service", event.source());
        header(headers, "aggregate_type", event.aggregateType());
        header(headers, "aggregate_id", event.aggregateId());
        header(headers, "business_key", event.businessKey());
        optionalHeader(headers, body, "saga_id", "sagaId");
        optionalHeader(headers, body, "correlation_id", "correlationId");
        optionalHeader(headers, body, "causation_id", "causationId");
        return new ProducerRecord<>(event.topic(), null, event.occurredAt().toEpochMilli(),
                event.key(), event.payload(), headers);
    }

    private ObjectNode parse(String payload) throws RejectedEvent {
        if (payload != null && payload.getBytes(StandardCharsets.UTF_8).length <= MAX_WIRE_BYTES) {
            try {
                if (reader.readTree(payload) instanceof ObjectNode body) {
                    return body;
                }
            } catch (JsonProcessingException ignored) {
                // Only the error category is persisted.
            }
        }
        throw new RejectedEvent("MALFORMED_PAYLOAD");
    }

    private static void validateBusinessPayload(ObjectNode body, boolean purchase) throws RejectedEvent {
        Instant occurred = timestamp(body.path("occurredAt"));
        boolean complete = purchase
                ? matching(body, "orderStatus", "COMPLETED") && matching(body, "paymentStatus", "PAID")
                    && !timestamp(body.path("completedAt")).isAfter(occurred)
                : matching(body, "newStatus", "COMPLETED");
        JsonNode items = body.path("items");
        if (!complete || !items.isArray() || items.isEmpty()) {
            throw new RejectedEvent("INVALID_COMPLETION_PAYLOAD");
        }
        Set<String> orderItems = new HashSet<>();
        Set<String> returnItems = new HashSet<>();
        for (JsonNode item : items) {
            String orderItem = item.path("orderItemId").textValue();
            if (!uuid(orderItem) || !orderItems.add(orderItem) || !validQuantity(item.path("quantity"))) {
                throw new RejectedEvent("INVALID_COMPLETION_LINE");
            }
            if (purchase) {
                String type = item.path("purchaseType").textValue();
                if (!("PRODUCT".equals(type) && uuid(item.path("productVariantId").textValue()))
                        && !"CUSTOM_BUNDLE".equals(type)) {
                    throw new RejectedEvent("INVALID_PURCHASE_LINE");
                }
            } else {
                String returnItem = item.path("returnItemId").textValue();
                String variant = item.path("productVariantId").textValue();
                String sku = item.path("sku").textValue();
                String reason = item.path("returnReason").textValue();
                if (!uuid(returnItem) || !returnItems.add(returnItem)
                        || (variant != null ? !uuid(variant) : sku == null || sku.isBlank() || sku.strip().length() > 100)
                        || reason == null || reason.isBlank()) {
                    throw new RejectedEvent("INVALID_RETURN_LINE");
                }
            }
        }
    }

    private static boolean validQuantity(JsonNode value) {
        if (!value.isNumber()) {
            return false;
        }
        BigDecimal decimal = value.decimalValue().stripTrailingZeros();
        return decimal.signum() > 0 && decimal.scale() <= 3 && decimal.precision() - decimal.scale() <= 11;
    }

    private static Instant timestamp(JsonNode value) throws RejectedEvent {
        try {
            if (value.isTextual()) {
                return OffsetDateTime.parse(value.textValue()).toInstant();
            }
            if (value.isNumber()) {
                BigDecimal seconds = value.decimalValue();
                if (seconds.compareTo(BigDecimal.valueOf(100000000000L)) > 0) {
                    seconds = seconds.movePointLeft(3);
                }
                long whole = seconds.toBigInteger().longValueExact();
                long nanos = seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).longValue();
                return Instant.ofEpochSecond(whole, nanos);
            }
        } catch (DateTimeException | ArithmeticException ignored) {
            // Invalid time is terminal for this row.
        }
        throw new RejectedEvent("INVALID_EVENT_TIME");
    }

    private static boolean uuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean matching(JsonNode body, String name, String expected) {
        return expected.equals(body.path(name).textValue());
    }

    private static void optionalHeader(RecordHeaders headers, ObjectNode body, String name, String field)
            throws RejectedEvent {
        JsonNode value = body.path(field);
        if (value.isTextual()) {
            header(headers, name, value.textValue());
        } else if (!value.isMissingNode() && !value.isNull()) {
            throw new RejectedEvent("INVALID_OPTIONAL_HEADER");
        }
    }

    private static void header(RecordHeaders headers, String key, String value) {
        headers.add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    static final class RejectedEvent extends Exception {
        final boolean retryable;

        RejectedEvent(String code) {
            this(code, false);
        }

        RejectedEvent(String code, boolean retryable) {
            super(code);
            this.retryable = retryable;
        }
    }
}
