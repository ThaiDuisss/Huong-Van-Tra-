package org.hvtra.catalog.outbox.polling;

import org.hvtra.catalog.outbox.event.CatalogEventType;

import java.time.Instant;
import java.util.Set;

record CatalogItemSyncEvent(long sequence, String claimToken, int attempts, String eventId, String eventType,
                            int eventVersion, String sourceService, String sagaId, String correlationId,
                            String causationId, String aggregateType, String aggregateId, String businessKey,
                            String messageKey, String topic, String payload, Instant occurredAt) {
    static final String TOPIC = "catalog.event.ITEM_SYNC";
    static final String SOURCE = "catalog-service";
    private static final Set<String> TYPES = Set.of(
            CatalogEventType.PRODUCT_VARIANT_CREATED, CatalogEventType.PRODUCT_VARIANT_UPDATED,
            CatalogEventType.PRODUCT_VARIANT_DELETED, CatalogEventType.INGREDIENT_CREATED,
            CatalogEventType.INGREDIENT_UPDATED, CatalogEventType.INGREDIENT_DELETED);

    static boolean supports(String eventType) {
        return eventType != null && TYPES.contains(eventType);
    }
}
