package org.hvtra.iventoryservice.integration.catalog.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.dto.CatalogItemSyncPayload;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogItemSyncInboxService Unit Tests")
class CatalogItemSyncInboxServiceTest {

    @Mock
    private InventoryInboxRepository inventoryInboxRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private CatalogItemSyncPayloadParser catalogItemSyncPayloadParser;

    @Mock
    private CatalogItemSyncMetadataResolver catalogItemSyncMetadataResolver;

    private CatalogItemSyncInboxService service;

    private final String consumerGroup = "inventory-catalog-item-sync";

    @BeforeEach
    void setUp() {
        service = new CatalogItemSyncInboxService(
                inventoryInboxRepository,
                inventoryItemRepository,
                catalogItemSyncPayloadParser,
                catalogItemSyncMetadataResolver
        );
        ReflectionTestUtils.setField(service, "consumerGroup", consumerGroup);
    }

    @Test
    @DisplayName("Should create new InventoryItem and record inbox when variant created event received")
    void handle_shouldCreateInventoryItem_whenVariantCreatedEvent() {
        UUID refId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        CatalogItemSyncPayload payload = new CatalogItemSyncPayload();
        payload.setEventId(eventId);
        payload.setEventType("PRODUCT_VARIANT_CREATED");
        payload.setItemType("PRODUCT_VARIANT");
        payload.setReferenceId(refId.toString());
        payload.setReferenceCode("PV-001");
        payload.setName("Trà Shan Tuyết Cổ Thụ 100g");
        payload.setUnitId(unitId.toString());
        payload.setStatus("ACTIVE");
        payload.setReorderPoint(20);
        payload.setIsDeleted(false);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("catalog.event.ITEM_SYNC", 0, 10L, refId.toString(), "{}");

        when(catalogItemSyncPayloadParser.parseCreateEvent(anyString())).thenReturn(Optional.of(payload));
        when(catalogItemSyncMetadataResolver.resolveEventId(eq(record), eq(payload))).thenReturn(eventId);
        when(inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)).thenReturn(Optional.empty());
        when(inventoryInboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.findByReferenceIdAndItemType(refId, InventoryItemType.PRODUCT_VARIANT)).thenReturn(Optional.empty());

        service.handle(record);

        ArgumentCaptor<InventoryItem> itemCaptor = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventoryItemRepository).save(itemCaptor.capture());
        InventoryItem savedItem = itemCaptor.getValue();

        assertThat(savedItem.getReferenceId()).isEqualTo(refId);
        assertThat(savedItem.getItemType()).isEqualTo(InventoryItemType.PRODUCT_VARIANT);
        assertThat(savedItem.getReferenceCode()).isEqualTo("PV-001");
        assertThat(savedItem.getName()).isEqualTo("Trà Shan Tuyết Cổ Thụ 100g");
        assertThat(savedItem.getUnitId()).isEqualTo(unitId);
        assertThat(savedItem.getStatus()).isEqualTo(InventoryStatus.ACTIVE);
        assertThat(savedItem.getReorderPoint()).isEqualTo(20);
        assertThat(savedItem.getIsDeleted()).isFalse();

        ArgumentCaptor<InventoryInbox> inboxCaptor = ArgumentCaptor.forClass(InventoryInbox.class);
        verify(inventoryInboxRepository).saveAndFlush(inboxCaptor.capture());
        InventoryInbox savedInbox = inboxCaptor.getValue();
        assertThat(savedInbox.getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(savedInbox.getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Should update existing InventoryItem when ingredient updated event received")
    void handle_shouldUpdateInventoryItem_whenIngredientUpdatedEvent() {
        UUID refId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        CatalogItemSyncPayload payload = new CatalogItemSyncPayload();
        payload.setEventId(eventId);
        payload.setEventType("INGREDIENT_UPDATED");
        payload.setItemType("INGREDIENT");
        payload.setReferenceId(refId.toString());
        payload.setReferenceCode("ING-002");
        payload.setName("Búp Trà Tươi Suối Giàng Mới");
        payload.setUnitId(unitId.toString());
        payload.setStatus("ACTIVE");
        payload.setReorderPoint(50);
        payload.setIsDeleted(false);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("catalog.event.ITEM_SYNC", 0, 11L, refId.toString(), "{}");

        InventoryItem existingItem = new InventoryItem();
        existingItem.setReferenceId(refId);
        existingItem.setItemType(InventoryItemType.INGREDIENT);
        existingItem.setName("Búp Trà Tươi Cũ");
        existingItem.setReferenceCode("ING-002");
        existingItem.setReorderPoint(10);
        existingItem.setIsDeleted(false);

        when(catalogItemSyncPayloadParser.parseCreateEvent(anyString())).thenReturn(Optional.of(payload));
        when(catalogItemSyncMetadataResolver.resolveEventId(eq(record), eq(payload))).thenReturn(eventId);
        when(inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)).thenReturn(Optional.empty());
        when(inventoryInboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.findByReferenceIdAndItemType(refId, InventoryItemType.INGREDIENT)).thenReturn(Optional.of(existingItem));

        service.handle(record);

        verify(inventoryItemRepository).save(existingItem);
        assertThat(existingItem.getName()).isEqualTo("Búp Trà Tươi Suối Giàng Mới");
        assertThat(existingItem.getReorderPoint()).isEqualTo(50);
        assertThat(existingItem.getUnitId()).isEqualTo(unitId);
    }

    @Test
    @DisplayName("Should soft delete InventoryItem when variant deleted event received")
    void handle_shouldSoftDeleteInventoryItem_whenVariantDeletedEvent() {
        UUID refId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        CatalogItemSyncPayload payload = new CatalogItemSyncPayload();
        payload.setEventId(eventId);
        payload.setEventType("PRODUCT_VARIANT_DELETED");
        payload.setItemType("PRODUCT_VARIANT");
        payload.setReferenceId(refId.toString());
        payload.setIsDeleted(true);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("catalog.event.ITEM_SYNC", 0, 12L, refId.toString(), "{}");

        InventoryItem existingItem = new InventoryItem();
        existingItem.setReferenceId(refId);
        existingItem.setItemType(InventoryItemType.PRODUCT_VARIANT);
        existingItem.setStatus(InventoryStatus.ACTIVE);
        existingItem.setIsDeleted(false);

        when(catalogItemSyncPayloadParser.parseCreateEvent(anyString())).thenReturn(Optional.of(payload));
        when(catalogItemSyncMetadataResolver.resolveEventId(eq(record), eq(payload))).thenReturn(eventId);
        when(inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)).thenReturn(Optional.empty());
        when(inventoryInboxRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryItemRepository.findByReferenceIdAndItemType(refId, InventoryItemType.PRODUCT_VARIANT)).thenReturn(Optional.of(existingItem));

        service.handle(record);

        verify(inventoryItemRepository).save(existingItem);
        assertThat(existingItem.getIsDeleted()).isTrue();
        assertThat(existingItem.getStatus()).isEqualTo(InventoryStatus.INACTIVE);
    }

    @Test
    @DisplayName("Should skip processing when event is already terminal in inbox (idempotency)")
    void handle_shouldDeduplicate_whenInboxAlreadyTerminal() {
        UUID refId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        CatalogItemSyncPayload payload = new CatalogItemSyncPayload();
        payload.setEventId(eventId);
        payload.setEventType("PRODUCT_VARIANT_CREATED");
        payload.setItemType("PRODUCT_VARIANT");
        payload.setReferenceId(refId.toString());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("catalog.event.ITEM_SYNC", 0, 13L, refId.toString(), "{}");

        InventoryInbox processedInbox = new InventoryInbox();
        processedInbox.setEventId(eventId);
        processedInbox.setStatus(InboxStatus.PROCESSED);
        processedInbox.setProcessedAt(Instant.now());

        when(catalogItemSyncPayloadParser.parseCreateEvent(anyString())).thenReturn(Optional.of(payload));
        when(catalogItemSyncMetadataResolver.resolveEventId(eq(record), eq(payload))).thenReturn(eventId);
        when(inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)).thenReturn(Optional.of(processedInbox));

        service.handle(record);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when duplicate event is still processing")
    void handle_shouldThrowException_whenInboxPending() {
        UUID refId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        CatalogItemSyncPayload payload = new CatalogItemSyncPayload();
        payload.setEventId(eventId);
        payload.setEventType("PRODUCT_VARIANT_CREATED");
        payload.setItemType("PRODUCT_VARIANT");
        payload.setReferenceId(refId.toString());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("catalog.event.ITEM_SYNC", 0, 14L, refId.toString(), "{}");

        InventoryInbox pendingInbox = new InventoryInbox();
        pendingInbox.setEventId(eventId);
        pendingInbox.setStatus(InboxStatus.PROCESSING);

        when(catalogItemSyncPayloadParser.parseCreateEvent(anyString())).thenReturn(Optional.of(payload));
        when(catalogItemSyncMetadataResolver.resolveEventId(eq(record), eq(payload))).thenReturn(eventId);
        when(inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)).thenReturn(Optional.of(pendingInbox));

        assertThatThrownBy(() -> service.handle(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate catalog sync event is not completed yet");

        verify(inventoryItemRepository, never()).save(any());
    }
}

