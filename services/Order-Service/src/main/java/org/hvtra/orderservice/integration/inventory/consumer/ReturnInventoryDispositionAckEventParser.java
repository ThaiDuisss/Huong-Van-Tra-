package org.hvtra.orderservice.integration.inventory.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionFailedEvent;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionProcessedEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReturnInventoryDispositionAckEventParser {

    private static final String PROCESSED_EVENT_TYPE = "RETURN_INVENTORY_DISPOSITION_PROCESSED";
    private static final String FAILED_EVENT_TYPE = "RETURN_INVENTORY_DISPOSITION_FAILED";

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
        ).filter(event -> PROCESSED_EVENT_TYPE.equals(event.getEventType()));
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
        ).filter(event -> FAILED_EVENT_TYPE.equals(event.getEventType()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
