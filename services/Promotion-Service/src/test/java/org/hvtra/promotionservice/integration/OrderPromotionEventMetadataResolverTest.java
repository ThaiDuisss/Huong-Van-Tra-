package org.hvtra.promotionservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.hvtra.promotionservice.integration.consumer.OrderPromotionEventMetadataResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderPromotionEventMetadataResolver")
class OrderPromotionEventMetadataResolverTest {

    final OrderPromotionEventMetadataResolver resolver =
            new OrderPromotionEventMetadataResolver(new EventMetadataResolver(new ObjectMapper()));

    ConsumerRecord<String, String> record(String topic) {
        return new ConsumerRecord<>(topic, 0, 7L, "order-key", "{}");
    }

    @Test
    @DisplayName("an event without type on the usage-commit topic becomes PROMOTION_USAGE_COMMIT_REQUESTED")
    void eventTypeFromUsageCommitTopic() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();

        resolver.normalizeBeforeEventId(record("promotion.usage-commit.requested"), event);

        assertThat(event.getEventType()).isEqualTo("PROMOTION_USAGE_COMMIT_REQUESTED");
    }

    @Test
    @DisplayName("an event without type on an unknown topic is not treated as a usage commit")
    void eventTypeFromUnknownTopic() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();

        resolver.normalizeBeforeEventId(record("order.created"), event);

        assertThat(event.getEventType()).isEqualTo("UNKNOWN_ORDER_EVENT");
    }

    @Test
    @DisplayName("an explicit event type is kept")
    void explicitEventTypeKept() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();
        event.setEventType("ORDER_CANCELLED");

        resolver.normalizeBeforeEventId(record("promotion.usage-commit.requested"), event);

        assertThat(event.getEventType()).isEqualTo("ORDER_CANCELLED");
    }

    @Test
    @DisplayName("the record key is the fallback event key")
    void eventKeyFromRecordKey() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();

        resolver.normalizeEventKey(record("promotion.usage-commit.requested"), event);

        assertThat(event.getEventKey()).isEqualTo("order-key");
    }

    @Test
    @DisplayName("source service defaults to order-service and correlation id to the order id")
    void defaultsWithoutHeaders() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();
        event.setOrderId("order-1");
        ConsumerRecord<String, String> record = record("promotion.usage-commit.requested");

        assertThat(resolver.sourceService(record)).isEqualTo("order-service");
        assertThat(resolver.correlationId(record, event)).isEqualTo("order-1");
        assertThat(resolver.serializeHeaders(record)).isEqualTo("{}");
    }

    @Test
    @DisplayName("headers override the defaults and are serialised")
    void headersOverrideDefaults() {
        OrderPromotionEventPayload event = new OrderPromotionEventPayload();
        event.setOrderId("order-1");
        ConsumerRecord<String, String> record = record("promotion.usage-commit.requested");
        record.headers().add("correlationId", "corr-9".getBytes(StandardCharsets.UTF_8));

        assertThat(resolver.correlationId(record, event)).isEqualTo("corr-9");
        assertThat(resolver.serializeHeaders(record)).isEqualTo("{\"correlationId\":\"corr-9\"}");
    }
}
