package org.hvtra.orderservice.integration.inventory.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionFailedEvent;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionProcessedEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReturnInventoryDispositionAckEventParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public Optional<ReturnInventoryDispositionProcessedEvent> parseProcessedCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                ReturnInventoryDispositionProcessedEvent.class,
                "return inventory disposition processed event",
                (event, metadata) -> {
                    if (isBlank(event.getCommandId())) {
                        event.setCommandId(metadata.aggregateId());
                    }
                    if (isBlank(event.getEventId())) {
                        event.setEventId(metadata.eventId());
                    }
                }
        );
    }

    public Optional<ReturnInventoryDispositionFailedEvent> parseFailedCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                ReturnInventoryDispositionFailedEvent.class,
                "return inventory disposition failed event",
                (event, metadata) -> {
                    if (isBlank(event.getCommandId())) {
                        event.setCommandId(metadata.aggregateId());
                    }
                    if (isBlank(event.getEventId())) {
                        event.setEventId(metadata.eventId());
                    }
                }
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
