package org.hvtra.customer.integration.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Payload ORDER_COMPLETED thật của Order-Service (fixture hợp đồng) phải đọc được bằng parser của consumer. */
class ProductCompletionCompatibilityTest {

    private static final Path FIXTURE = Path.of("../Order-Service/src/test/resources/contracts/product-completed-v1.json");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final DebeziumOutboxEventPayloadParser parser = new DebeziumOutboxEventPayloadParser(objectMapper);

    @Test
    void plainOutboxPayloadIsReadable() throws Exception {
        String payload = Files.readString(FIXTURE);

        OrderCompletedEvent event = parser
                .parseCreateEvent(payload, OrderCompletedEvent.class, "ORDER_COMPLETED", null)
                .orElseThrow();

        assertFixture(event);
    }

    @Test
    void schemaWrappedEventRouterPayloadIsReadable() throws Exception {
        String payload = Files.readString(FIXTURE);
        // JsonConverter schemas.enable=true bọc payload (dạng chuỗi) trong {schema, payload}.
        String wrapped = objectMapper.writeValueAsString(Map.of(
                "schema", Map.of("type", "string"),
                "payload", payload
        ));

        OrderCompletedEvent event = parser
                .parseCreateEvent(wrapped, OrderCompletedEvent.class, "ORDER_COMPLETED", null)
                .orElseThrow();

        assertFixture(event);
    }

    private void assertFixture(OrderCompletedEvent event) {
        assertThat(event.getEventId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(event.getEventType()).isEqualTo("ORDER_COMPLETED");
        assertThat(event.getOrderId()).isEqualTo(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        assertThat(event.getOrderCode()).isEqualTo("FIXTURE-PRODUCT-ONLY");
        assertThat(event.getCustomerId()).isEqualTo(UUID.fromString("33333333-3333-4333-8333-333333333333"));
        assertThat(event.getFinalAmount()).isEqualByComparingTo("200");
        assertThat(event.getCompletedAt()).isEqualTo(Instant.parse("2026-10-01T08:00:00Z"));
    }
}
