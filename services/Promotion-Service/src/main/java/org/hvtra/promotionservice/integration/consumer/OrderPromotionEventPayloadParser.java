package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderPromotionEventPayloadParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public OrderPromotionEventPayload parse(String rawPayload) {
        return parseCreateEvent(rawPayload).orElseThrow(() ->
                new IllegalArgumentException("Order promotion event is not a Debezium create event")
        );
    }

    public Optional<OrderPromotionEventPayload> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                OrderPromotionEventPayload.class,
                "order promotion event",
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
