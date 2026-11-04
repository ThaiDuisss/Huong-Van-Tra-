package org.hvtra.notificationservice.integration.consumer;

import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductCompletionCompatibilityTest {
    @Test
    void additiveProductV1FieldsPreserveInvoiceItemsInPlainAndConnectEnvelopes() throws Exception {
        String payload = Files.readString(Path.of("../Order-Service/src/test/resources/contracts/product-completed-v1.json"));
        var parser = new OrderNotificationEventPayloadParser(
                new DebeziumOutboxEventPayloadParser(Jackson2ObjectMapperBuilder.json().build()));
        for (String raw : new String[]{payload, "{\"schema\":null,\"payload\":" + payload + "}"}) {
            var event = parser.parseCreateEvent(raw).orElseThrow();
            assertEquals("ORDER_COMPLETED", event.getEventType());
            assertEquals("22222222-2222-4222-8222-222222222222", event.getOrderId());
            assertEquals(1, event.getItems().size());
            assertEquals("FIXTURE-TEA", event.getItems().get(0).getSku());
            assertEquals(2, event.getItems().get(0).getQuantity().intValueExact());
        }
    }
}
