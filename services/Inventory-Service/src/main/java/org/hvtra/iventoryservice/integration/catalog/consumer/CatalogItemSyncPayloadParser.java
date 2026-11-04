package org.hvtra.iventoryservice.integration.catalog.consumer;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.iventoryservice.integration.catalog.dto.CatalogItemSyncPayload;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CatalogItemSyncPayloadParser {

    private final DebeziumOutboxEventPayloadParser parser;

    public Optional<CatalogItemSyncPayload> parseCreateEvent(String rawPayload) {
        return parser.parseCreateEvent(
                rawPayload,
                CatalogItemSyncPayload.class,
                "catalog item sync event",
                (event, metadata) -> {
                    if (isBlank(event.getReferenceId())) {
                        event.setReferenceId(metadata.aggregateId());
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

