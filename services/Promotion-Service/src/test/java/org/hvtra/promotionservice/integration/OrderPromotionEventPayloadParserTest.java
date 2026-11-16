package org.hvtra.promotionservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.hvtra.promotionservice.integration.consumer.OrderPromotionEventPayloadParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderPromotionEventPayloadParser")
class OrderPromotionEventPayloadParserTest {

    static final String USAGE_ID = "550e8400-e29b-41d4-a716-446655440000";

    final OrderPromotionEventPayloadParser parser =
            new OrderPromotionEventPayloadParser(new DebeziumOutboxEventPayloadParser(new ObjectMapper()));

    String debeziumEvent(String op, String businessPayload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mapper.createObjectNode()
                .put("op", op)
                .set("after", mapper.createObjectNode()
                        .put("event_id", "evt-1")
                        .put("event_type", "PROMOTION_USAGE_COMMIT_REQUESTED")
                        .put("aggregate_id", "order-from-outbox")
                        .put("causation_id", "cause-from-outbox")
                        .put("payload", businessPayload)));
    }

    @Test
    @DisplayName("a plain business payload is parsed as is")
    void plainPayload_parsed() {
        OrderPromotionEventPayload event = parser.parse(
                "{\"eventType\":\"PROMOTION_USAGE_COMMIT_REQUESTED\",\"orderId\":\"order-1\",\"usageIds\":[\""
                        + USAGE_ID + "\"]}");

        assertThat(event.getEventType()).isEqualTo("PROMOTION_USAGE_COMMIT_REQUESTED");
        assertThat(event.getOrderId()).isEqualTo("order-1");
        assertThat(event.getUsageIds()).containsExactly(USAGE_ID);
    }

    @Test
    @DisplayName("a Debezium create event fills the missing order id, causation id and event metadata from the outbox row")
    void createEvent_hydratedFromOutboxRow() throws Exception {
        Optional<OrderPromotionEventPayload> parsed = parser.parseCreateEvent(
                debeziumEvent("c", "{\"usageIds\":[\"" + USAGE_ID + "\"]}"));

        assertThat(parsed).isPresent();
        OrderPromotionEventPayload event = parsed.get();
        assertThat(event.getUsageIds()).containsExactly(USAGE_ID);
        assertThat(event.getOrderId()).isEqualTo("order-from-outbox");
        assertThat(event.getCausationId()).isEqualTo("cause-from-outbox");
        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getEventType()).isEqualTo("PROMOTION_USAGE_COMMIT_REQUESTED");
    }

    @Test
    @DisplayName("order id and causation id of the business payload win over the outbox row")
    void createEvent_payloadValuesKept() throws Exception {
        OrderPromotionEventPayload event = parser.parse(
                debeziumEvent("c", "{\"orderId\":\"order-1\",\"causationId\":\"cause-1\"}"));

        assertThat(event.getOrderId()).isEqualTo("order-1");
        assertThat(event.getCausationId()).isEqualTo("cause-1");
    }

    @Test
    @DisplayName("update and delete row events are not create events")
    void nonCreateEvent_empty() throws Exception {
        String update = debeziumEvent("u", "{\"orderId\":\"order-1\"}");
        String delete = debeziumEvent("d", "{\"orderId\":\"order-1\"}");

        assertThat(parser.parseCreateEvent(update)).isEmpty();
        assertThat(parser.parseCreateEvent(delete)).isEmpty();
        assertThatThrownBy(() -> parser.parse(update))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order promotion event is not a Debezium create event");
    }

    @Test
    @DisplayName("tombstones are skipped")
    void tombstone_empty() {
        assertThat(parser.parseCreateEvent(null)).isEmpty();
        assertThat(parser.parseCreateEvent("  ")).isEmpty();
    }

    @Test
    @DisplayName("an unreadable payload is rejected")
    void invalidJson_throws() {
        assertThatThrownBy(() -> parser.parseCreateEvent("{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order promotion event");
    }
}
