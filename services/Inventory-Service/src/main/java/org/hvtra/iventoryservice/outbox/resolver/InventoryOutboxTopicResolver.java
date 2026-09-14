package org.hvtra.iventoryservice.outbox.resolver;

import org.hvtra.commonservice.kafka.outbox.resolver.OutboxTopicResolver;
import org.hvtra.iventoryservice.outbox.event.InventoryEventType;
import org.springframework.stereotype.Component;

@Component
public class InventoryOutboxTopicResolver implements OutboxTopicResolver {

    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case InventoryEventType.RETURN_INVENTORY_DISPOSITION_PROCESSED ->
                    "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED";
            case InventoryEventType.RETURN_INVENTORY_DISPOSITION_FAILED ->
                    "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED";
            default -> throw new IllegalArgumentException("Unknown inventory event type: " + eventType);
        };
    }
}
