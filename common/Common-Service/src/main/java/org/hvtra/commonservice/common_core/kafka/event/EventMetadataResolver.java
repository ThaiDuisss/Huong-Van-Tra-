package org.hvtra.commonservice.kafka.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventMetadataResolver {

    private final ObjectMapper objectMapper;

//Bổ sung eventId và eventType vào payload nếu payload đang thiếu.
    public void normalizeBeforeEventId(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
            Function<String, String> topicEventTypeResolver,
            String unknownEventType,
            String eventName
    ) {
        if (isBlank(event.getEventId())) {
            event.setEventId(headerValue(record, null, EventHeaders.EVENT_ID, "eventId"));
        }
        if (isBlank(event.getEventType())) {
            event.setEventType(resolveEventType(record, topicEventTypeResolver, unknownEventType, eventName));
        }
    }
//Bổ sung eventKey vào payload nếu payload đang thiếu.
    public void normalizeEventKey(ConsumerRecord<String, String> record, CommonEventPayload event) {
        if (isBlank(event.getEventKey())) {
            event.setEventKey(headerValue(record, record.key(), "business_key", "businessKey", "message_key", "messageKey"));
        }
    }

    public String resolveEventId(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
            String eventName
    ) {
        String headerEventId = headerValue(record, null, EventHeaders.EVENT_ID, "eventId");
        if (!isBlank(headerEventId)) {
            return headerEventId;
        }

        if (!isBlank(event.getEventId())) {
            return event.getEventId();
        }

        if (!isBlank(event.getEventKey())) {
            log.warn("{} thiếu eventId, fallback sang legacy eventKey. topic={}, partition={}, offset={}",
                    eventName, record.topic(), record.partition(), record.offset());
            return event.getEventKey();
        }

        String fallback = record.topic() + "-" + record.partition() + "-" + record.offset();
        log.warn("{} thiếu event_id header, payload.eventId và payload.eventKey. Fallback inbox id={}",
                eventName, fallback);
        return fallback;
    }

    public String sourceService(ConsumerRecord<String, String> record, String defaultValue) {
        return headerValue(record, defaultValue, EventHeaders.SOURCE_SERVICE, "sourceService", "source");
    }

    public String correlationId(ConsumerRecord<String, String> record, String defaultValue) {
        return headerValue(record, defaultValue, EventHeaders.CORRELATION_ID, "correlationId");
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        record.headers().forEach(header -> headers.put(
                header.key(),
                header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8)
        ));

        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize kafka headers", ex);
        }
    }

    public String headerValue(
            ConsumerRecord<String, String> record,
            String headerName,
            String defaultValue
    ) {
        return headerValue(record, defaultValue, new String[]{headerName});
    }

    public String headerValue(
            ConsumerRecord<String, String> record,
            String defaultValue,
            String... headerNames
    ) {
        if (headerNames == null) {
            return defaultValue;
        }

        for (String headerName : headerNames) {
            if (isBlank(headerName)) {
                continue;
            }

            Header header = record.headers().lastHeader(headerName);
            if (header != null && header.value() != null) {
                String value = new String(header.value(), StandardCharsets.UTF_8);
                if (!isBlank(value)) {
                    return value;
                }
            }
        }

        return defaultValue;
    }

    private String resolveEventType(
            ConsumerRecord<String, String> record,
            Function<String, String> topicEventTypeResolver,
            String unknownEventType,
            String eventName
    ) {
        String headerEventType = headerValue(record, null, EventHeaders.EVENT_TYPE, "eventType");
        if (!isBlank(headerEventType)) {
            return headerEventType;
        }

        String topicEventType = topicEventTypeResolver == null ? null : topicEventTypeResolver.apply(record.topic());
        if (!isBlank(topicEventType)) {
            return topicEventType;
        }

        log.warn("Không xác định được {} eventType. topic={}, partition={}, offset={}",
                eventName, record.topic(), record.partition(), record.offset());
        return unknownEventType;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
