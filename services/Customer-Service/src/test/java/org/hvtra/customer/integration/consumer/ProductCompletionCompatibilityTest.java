package org.hvtra.customer.integration.consumer;

import org.hvtra.customer.integration.event.OrderCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductCompletionCompatibilityTest {
    @Test
    void additiveProductV1FieldsRemainReadableByExistingKafkaDeserializer() throws Exception {
        Path fixture = Path.of("../Order-Service/src/test/resources/contracts/product-completed-v1.json");
        try (var reader = new JsonDeserializer<>(OrderCompletedEvent.class, false)) {
            OrderCompletedEvent event = reader.deserialize("order.completed", Files.readAllBytes(fixture));
            assertEquals("22222222-2222-4222-8222-222222222222", event.orderId().toString());
            assertNotNull(event.customerId());
            assertEquals(200, event.finalAmount().intValueExact());
            assertEquals(1, event.occurredAt().getSecond());
        }
    }
}
