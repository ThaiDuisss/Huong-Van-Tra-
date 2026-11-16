package org.hvtra.notificationservice.integration.consumer;

import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderNotificationEventPayloadParserTest {

    private final OrderNotificationEventPayloadParser parser = new OrderNotificationEventPayloadParser(
            new DebeziumOutboxEventPayloadParser(Jackson2ObjectMapperBuilder.json().build()));

    private static final String OUTBOX_COLUMNS = "\"event_id\":\"evt-row\",\"event_type\":\"ORDER_CANCELLED\","
            + "\"business_key\":\"POS-9\",\"aggregate_id\":\"order-row\",\"saga_id\":\"saga-row\","
            + "\"correlation_id\":\"corr-row\",\"causation_id\":\"cause-row\"";

    @Test
    void outboxRowColumnsFillFieldsMissingFromPayload() {
        String raw = "{\"op\":\"c\",\"after\":{" + OUTBOX_COLUMNS + ",\"payload\":{\"orderCode\":\"POS-9\"}}}";

        OrderNotificationEventPayload event = parser.parseCreateEvent(raw).orElseThrow();

        assertThat(event.getEventId()).isEqualTo("evt-row");
        assertThat(event.getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(event.getEventKey()).isEqualTo("POS-9");
        assertThat(event.getOrderId()).isEqualTo("order-row");
        assertThat(event.getSagaId()).isEqualTo("saga-row");
        assertThat(event.getCorrelationId()).isEqualTo("corr-row");
        assertThat(event.getCausationId()).isEqualTo("cause-row");
        assertThat(event.getOrderCode()).isEqualTo("POS-9");
    }

    @Test
    void valuesAlreadyInPayloadAreNotOverwrittenByOutboxRowColumns() {
        String payload = "{\"eventId\":\"evt-payload\",\"eventType\":\"RETURN_CREATED\",\"orderId\":\"order-payload\","
                + "\"sagaId\":\"saga-payload\",\"correlationId\":\"corr-payload\",\"causationId\":\"cause-payload\"}";
        String raw = "{\"op\":\"c\",\"after\":{" + OUTBOX_COLUMNS + ",\"payload\":" + payload + "}}";

        OrderNotificationEventPayload event = parser.parseCreateEvent(raw).orElseThrow();

        assertThat(event.getEventId()).isEqualTo("evt-payload");
        assertThat(event.getEventType()).isEqualTo("RETURN_CREATED");
        assertThat(event.getOrderId()).isEqualTo("order-payload");
        assertThat(event.getSagaId()).isEqualTo("saga-payload");
        assertThat(event.getCorrelationId()).isEqualTo("corr-payload");
        assertThat(event.getCausationId()).isEqualTo("cause-payload");
    }

    @Test
    void snapshotReadIsTreatedLikeAnInsert() {
        String raw = "{\"op\":\"r\",\"after\":{" + OUTBOX_COLUMNS + ",\"payload\":\"{\\\"userId\\\":\\\"user-1\\\"}\"}}";

        OrderNotificationEventPayload event = parser.parseCreateEvent(raw).orElseThrow();

        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getOrderId()).isEqualTo("order-row");
    }

    @Test
    void insertWithoutAfterImageAndNonInsertOperationsYieldNothing() {
        assertThat(parser.parseCreateEvent("{\"op\":\"c\",\"after\":null}")).isEmpty();
        assertThat(parser.parseCreateEvent("{\"op\":\"u\",\"after\":{" + OUTBOX_COLUMNS + "}}")).isEmpty();
        assertThat(parser.parseCreateEvent("{\"op\":\"d\",\"before\":{" + OUTBOX_COLUMNS + "}}")).isEmpty();
        assertThat(parser.parseCreateEvent(null)).isEmpty();
        assertThat(parser.parseCreateEvent(" ")).isEmpty();
    }

    @Test
    void plainPayloadIsParsedWithoutOutboxHydration() {
        OrderNotificationEventPayload event = parser.parseCreateEvent(
                "{\"eventType\":\"ORDER_COMPLETED\",\"orderCode\":\"POS-1\",\"finalAmount\":125000.50}").orElseThrow();

        assertThat(event.getEventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(event.getFinalAmount()).isEqualByComparingTo("125000.50");
        assertThat(event.getOrderId()).isNull();
        assertThat(event.getSagaId()).isNull();
    }

    @Test
    void jsonEncodedAsStringIsUnwrapped() {
        OrderNotificationEventPayload event = parser.parseCreateEvent(
                "\"{\\\"eventType\\\":\\\"ORDER_CANCELLED\\\",\\\"orderId\\\":\\\"order-1\\\"}\"").orElseThrow();

        assertThat(event.getEventType()).isEqualTo("ORDER_CANCELLED");
        assertThat(event.getOrderId()).isEqualTo("order-1");
    }

    @Test
    void invalidJsonIsReportedAsIllegalArgument() {
        assertThatThrownBy(() -> parser.parseCreateEvent("{\"eventType\":"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order notification event");
    }
}
