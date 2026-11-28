package org.hvtra.iventoryservice.outbox.polling;

import org.hvtra.iventoryservice.outbox.event.InventoryEventType;

import java.time.Instant;

public record PollingInventoryEvent(
        String outboxId,
        String claimToken,
        int attempts,
        String eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        String key,
        String businessKey,
        String topic,
        String source,
        String sagaId,
        String correlationId,
        String causationId,
        String payload,
        Instant occurredAt
) {
    public static boolean supports(String eventType, String topic) {
        return (InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED.equals(eventType)
                && "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED".equals(topic))
                || (InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED.equals(eventType)
                        && "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED".equals(topic))
                || (InventoryEventType.CUSTOM_BUNDLE_PACKED.equals(eventType)
                        && "inventory.event.CUSTOM_BUNDLE_PACKED".equals(topic));
    }
}
