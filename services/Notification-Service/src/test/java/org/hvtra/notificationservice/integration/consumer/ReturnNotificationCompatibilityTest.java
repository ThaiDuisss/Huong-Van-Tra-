package org.hvtra.notificationservice.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnNotificationCompatibilityTest {

    @ParameterizedTest
    @CsvSource({
            "RETURN_CREATED,CREATED",
            "RETURN_RECEIVED,RECEIVED",
            "RETURN_CANCELLED,CANCELLED",
            "RETURN_REFUND_COMPLETED,COMPLETED",
            "RETURN_COMPLETED,COMPLETED"
    })
    void plainPollingJsonReturnEventsParseCleanlyThroughOrderNotificationParser(
            String eventType,
            String newStatus
    ) throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        OrderNotificationEventPayloadParser parser = new OrderNotificationEventPayloadParser(
                new DebeziumOutboxEventPayloadParser(mapper));

        String eventId = UUID.randomUUID().toString();
        String returnId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("eventType", eventType);
        body.put("eventKey", returnId);
        body.put("returnId", returnId);
        body.put("returnCode", "RET-001");
        body.put("orderId", orderId);
        body.put("orderCode", "ORD-001");
        body.put("newStatus", newStatus);
        body.put("title", "Trạng thái hoàn hàng");
        body.put("message", "Cập nhật hoàn hàng");
        body.put("occurredAt", "2026-11-26T12:00:00Z");
        body.put("items", List.of(Map.of(
                "returnItemId", UUID.randomUUID().toString(),
                "orderItemId", UUID.randomUUID().toString(),
                "sku", "TRA-001",
                "quantity", new BigDecimal("1.000")
        )));

        String raw = mapper.writeValueAsString(body);
        OrderNotificationEventPayload parsed = parser.parseCreateEvent(raw).orElseThrow();
        assertEquals(eventId, parsed.getEventId());
        assertEquals(eventType, parsed.getEventType());
        assertEquals(orderId, parsed.getOrderId());
        assertEquals(newStatus, parsed.getNewStatus());
    }
}
