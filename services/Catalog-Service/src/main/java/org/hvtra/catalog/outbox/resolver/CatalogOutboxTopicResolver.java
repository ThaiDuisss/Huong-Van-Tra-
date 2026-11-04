package org.hvtra.catalog.outbox.resolver;

import org.hvtra.catalog.outbox.event.CatalogEventType;
import org.hvtra.commonservice.common_core.kafka.outbox.resolver.OutboxTopicResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CatalogOutboxTopicResolver implements OutboxTopicResolver {

    @Value("${catalog.events.topics.item-sync:catalog.event.ITEM_SYNC}")
    private String itemSyncTopic;

    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case CatalogEventType.PRODUCT_VARIANT_CREATED,
                 CatalogEventType.PRODUCT_VARIANT_UPDATED,
                 CatalogEventType.PRODUCT_VARIANT_DELETED,
                 CatalogEventType.INGREDIENT_CREATED,
                 CatalogEventType.INGREDIENT_UPDATED,
                 CatalogEventType.INGREDIENT_DELETED -> itemSyncTopic;
            default -> throw new IllegalArgumentException("Unknown catalog event type: " + eventType);
        };
    }
}

