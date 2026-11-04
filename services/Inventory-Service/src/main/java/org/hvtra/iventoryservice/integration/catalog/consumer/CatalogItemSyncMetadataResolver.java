package org.hvtra.iventoryservice.integration.catalog.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.iventoryservice.integration.catalog.dto.CatalogItemSyncPayload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogItemSyncMetadataResolver {

    private static final String UNKNOWN_CATALOG_EVENT = "UNKNOWN_CATALOG_EVENT";

    private final EventMetadataResolver resolver;

    public void normalizeBeforeEventId(ConsumerRecord<String, String> record, CatalogItemSyncPayload event) {
        resolver.normalizeBeforeEventId(record, event, this::eventTypeFromTopic, UNKNOWN_CATALOG_EVENT, "catalog item sync event");
    }

    public void normalizeEventKey(ConsumerRecord<String, String> record, CatalogItemSyncPayload event) {
        resolver.normalizeEventKey(record, event);
    }

    public String resolveEventId(ConsumerRecord<String, String> record, CatalogItemSyncPayload event) {
        return resolver.resolveEventId(record, event, "Catalog item sync event");
    }

    public String sourceService(ConsumerRecord<String, String> record) {
        return resolver.sourceService(record, "catalog-service");
    }

    public String correlationId(ConsumerRecord<String, String> record, CatalogItemSyncPayload event) {
        return resolver.correlationId(record, event.getReferenceId());
    }

    public String serializeHeaders(ConsumerRecord<String, String> record) {
        return resolver.serializeHeaders(record);
    }

    private String eventTypeFromTopic(String topic) {
        return null;
    }
}

