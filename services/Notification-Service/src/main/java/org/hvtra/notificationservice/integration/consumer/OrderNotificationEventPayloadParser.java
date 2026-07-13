package org.hvtra.notificationservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderNotificationEventPayloadParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public Optional<OrderNotificationEventPayload> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                OrderNotificationEventPayload.class,
                "order notification event",
                (event, metadata) -> {
                    if (isBlank(event.getOrderId())) {
                        event.setOrderId(metadata.aggregateId());
                    }
                    if (isBlank(event.getSagaId())) {
                        event.setSagaId(metadata.sagaId());
                    }
                    if (isBlank(event.getCorrelationId())) {
                        event.setCorrelationId(metadata.correlationId());
                    }
                    if (isBlank(event.getCausationId())) {
                        event.setCausationId(metadata.causationId());
                    }
                }
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
