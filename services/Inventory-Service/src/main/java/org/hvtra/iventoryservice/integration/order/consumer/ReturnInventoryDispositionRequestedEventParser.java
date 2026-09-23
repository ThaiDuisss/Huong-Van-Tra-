package org.hvtra.iventoryservice.integration.order.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.iventoryservice.integration.order.dto.ReturnInventoryDispositionRequestedEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReturnInventoryDispositionRequestedEventParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public Optional<ReturnInventoryDispositionRequestedEvent> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                ReturnInventoryDispositionRequestedEvent.class,
                "return inventory disposition event",
                (event, metadata) -> {
                    if (isBlank(event.getCommandId())) {
                        event.setCommandId(metadata.aggregateId());
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
