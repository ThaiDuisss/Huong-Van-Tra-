




package org.hvtra.catalog.outbox.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.entity.Ingredient;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.outbox.entity.CatalogOutboxEvent;
import org.hvtra.catalog.outbox.event.CatalogEventType;
import org.hvtra.catalog.outbox.resolver.CatalogOutboxTopicResolver;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogOutboxPublisher {

    private static final String SOURCE = "catalog-service";
    private static final String AGGREGATE_TYPE_VARIANT = "PRODUCT_VARIANT";
    private static final String AGGREGATE_TYPE_INGREDIENT = "INGREDIENT";

    private final OutboxEventService<CatalogOutboxEvent> catalogOutboxEventService;
    private final CatalogOutboxTopicResolver topicResolver;

    public void publishVariantCreated(ProductVariant variant) {
        publishVariantEvent(variant, CatalogEventType.PRODUCT_VARIANT_CREATED, false);
    }

    /** Stable event identity for the guarded commercial importer; ordinary event behavior is unchanged. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String publishImportedVariantCreated(ProductVariant variant, String importIdentity) {
        if (importIdentity == null || importIdentity.isBlank()) throw new IllegalArgumentException("IMPORT_EVENT_IDENTITY_REQUIRED");
        String eventId = UUID.nameUUIDFromBytes(("catalog-commercial-import:" + importIdentity).getBytes(StandardCharsets.UTF_8)).toString();
        String businessKey = "commercial-import:" + eventId;
        publishVariantEvent(variant, CatalogEventType.PRODUCT_VARIANT_CREATED, false, eventId, businessKey);
        return eventId;
    }

    public void publishVariantUpdated(ProductVariant variant) {
        publishVariantEvent(variant, CatalogEventType.PRODUCT_VARIANT_UPDATED, false);
    }

    public void publishVariantDeleted(ProductVariant variant) {
        publishVariantEvent(variant, CatalogEventType.PRODUCT_VARIANT_DELETED, true);
    }

    public void publishIngredientCreated(Ingredient ingredient) {
        publishIngredientEvent(ingredient, CatalogEventType.INGREDIENT_CREATED, false);
    }

    public void publishIngredientUpdated(Ingredient ingredient) {
        publishIngredientEvent(ingredient, CatalogEventType.INGREDIENT_UPDATED, false);
    }

    public void publishIngredientDeleted(Ingredient ingredient) {
        publishIngredientEvent(ingredient, CatalogEventType.INGREDIENT_DELETED, true);
    }

    private void publishVariantEvent(ProductVariant variant, String eventType, boolean isDeleted) {
        String eventId = UUID.randomUUID().toString();
        String aggregateId = variant.getId().toString();
        String businessKey = aggregateId + ":" + eventType + ":" + System.currentTimeMillis();
        publishVariantEvent(variant, eventType, isDeleted, eventId, businessKey);
    }

    private void publishVariantEvent(ProductVariant variant, String eventType, boolean isDeleted, String eventId, String businessKey) {
        String aggregateId = variant.getId().toString();
        String topic = topicResolver.resolveTopic(eventType);
        Instant now = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("itemType", "PRODUCT_VARIANT");
        payload.put("referenceId", aggregateId);
        payload.put("referenceCode", variant.getSku());
        payload.put("name", variant.getVariantName());
        payload.put("unitId", variant.getUnit() != null ? variant.getUnit().getId().toString() : null);
        payload.put("status", variant.getStatus() != null ? variant.getStatus().name() : "ACTIVE");
        payload.put("reorderPoint", variant.getProductionThreshold() != null ? variant.getProductionThreshold().intValue() : 0);
        payload.put("isDeleted", isDeleted);
        payload.put("occurredAt", now.toString());

        Map<String, Object> headers = buildHeaders(eventId, eventType, AGGREGATE_TYPE_VARIANT, aggregateId, businessKey);

        catalogOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE_VARIANT,
                aggregateId,
                aggregateId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                null,
                null,
                aggregateId,
                payload,
                headers
        ));

        log.info("Published outbox {}. variantId={}, sku={}, eventId={}", eventType, aggregateId, variant.getSku(), eventId);
    }

    private void publishIngredientEvent(Ingredient ingredient, String eventType, boolean isDeleted) {
        String eventId = UUID.randomUUID().toString();
        String aggregateId = ingredient.getId().toString();
        String businessKey = aggregateId + ":" + eventType + ":" + System.currentTimeMillis();
        String topic = topicResolver.resolveTopic(eventType);
        Instant now = Instant.now();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("itemType", "INGREDIENT");
        payload.put("referenceId", aggregateId);
        payload.put("referenceCode", ingredient.getIngredientCode());
        payload.put("name", ingredient.getName());
        payload.put("unitId", ingredient.getUnit() != null ? ingredient.getUnit().getId().toString() : null);
        payload.put("status", ingredient.getStatus() != null ? ingredient.getStatus().name() : "ACTIVE");
        payload.put("reorderPoint", ingredient.getReorderPoint() != null ? ingredient.getReorderPoint() : 0);
        payload.put("isDeleted", isDeleted);
        payload.put("occurredAt", now.toString());

        Map<String, Object> headers = buildHeaders(eventId, eventType, AGGREGATE_TYPE_INGREDIENT, aggregateId, businessKey);

        catalogOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE_INGREDIENT,
                aggregateId,
                aggregateId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                null,
                null,
                aggregateId,
                payload,
                headers
        ));

        log.info("Published outbox {}. ingredientId={}, code={}, eventId={}", eventType, aggregateId, ingredient.getIngredientCode(), eventId);
    }

    private Map<String, Object> buildHeaders(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            String businessKey
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("event_id", eventId);
        headers.put("event_type", eventType);
        headers.put("event_version", 1);
        headers.put("source_service", SOURCE);
        headers.put("aggregate_type", aggregateType);
        headers.put("aggregate_id", aggregateId);
        headers.put("business_key", businessKey);
        return headers;
    }
}

