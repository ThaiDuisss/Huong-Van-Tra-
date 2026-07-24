package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.event.DebeziumOutboxEventPayloadParser;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderInventoryEventPayloadParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public OrderInventoryEventPayload parse(String rawPayload) {
        return parseCreateEvent(rawPayload).orElseThrow(() ->
                new IllegalArgumentException("Order inventory event is not a Debezium create event")
        );
    }

    public Optional<OrderInventoryEventPayload> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                OrderInventoryEventPayload.class,
                "order inventory event",
                (event, metadata) -> {
                    if (isBlank(event.getOrderId())) {
                        event.setOrderId(metadata.aggregateId());
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
