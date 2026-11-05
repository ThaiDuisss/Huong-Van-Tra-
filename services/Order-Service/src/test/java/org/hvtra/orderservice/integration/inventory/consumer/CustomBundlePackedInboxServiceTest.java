package org.hvtra.orderservice.integration.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.service.OrderCustomBundleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomBundlePackedInboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final String GROUP = "unit-order-bundle";
    @Mock private OrderInboxRepository inboxes;
    @Mock private OrderCustomBundleService bundles;
    private CustomBundlePackedInboxService service;
    private final UUID orderId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        service = new CustomBundlePackedInboxService(inboxes, new DebeziumOutboxEventPayloadParser(mapper),
                new EventMetadataResolver(mapper), bundles);
        ReflectionTestUtils.setField(service, "consumerGroup", GROUP);
    }

    @Test
    void validEventClaimsInboxAndMarksExactItemPackedBeforeFinishing() {
        List<InboxStatus> savedStates = new ArrayList<>();
        when(inboxes.saveAndFlush(any())).thenAnswer(call -> {
            OrderInbox inbox = call.getArgument(0);
            savedStates.add(inbox.getStatus());
            return inbox;
        });
        when(bundles.markPacked(orderId, itemId, "PACK-UNIT")).thenReturn(true);
        ConsumerRecord<String, String> record = record(payload(orderId.toString(), itemId.toString()));
        atFixedTime(() -> service.handle(record));
        assertEquals(List.of(InboxStatus.RECEIVED, InboxStatus.PROCESSED), savedStates);
        ArgumentCaptor<OrderInbox> captured = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxes, times(2)).saveAndFlush(captured.capture());
        var inbox = captured.getValue();
        assertEquals("event-unit", inbox.getEventId());
        assertEquals(GROUP, inbox.getConsumerGroup());
        assertEquals("packing-unit", inbox.getEntityId());
        assertEquals(record.value(), inbox.getPayloadJson());
        assertEquals(record.offset(), inbox.getOffsetNo());
        assertEquals(record.topic(), inbox.getTopic());
        assertEquals("inventory-service", inbox.getSourceService());
        assertEquals(NOW, inbox.getReceivedAt());
        assertEquals(NOW, inbox.getProcessedAt());
        verify(bundles).markPacked(orderId, itemId, "PACK-UNIT");
    }

    @ParameterizedTest
    @EnumSource(value = InboxStatus.class, names = {"PROCESSED", "IGNORED", "FAILED", "DEAD"})
    void terminalInboxReplayDoesNotUpdateBundleOrWriteAgain(InboxStatus status) {
        OrderInbox inbox = new OrderInbox();
        inbox.setStatus(status);
        when(inboxes.findByEventIdAndConsumerGroup("event-unit", GROUP)).thenReturn(Optional.of(inbox));
        service.handle(record(payload(orderId.toString(), itemId.toString())));
        assertEquals(status, inbox.getStatus());
        verifyNoInteractions(bundles);
        verify(inboxes, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({"not-uuid,valid", "valid,not-uuid", ",valid", "valid,"})
    void invalidOrderIdentityIsFailedAndNeverDispatchesToBusinessService(String order, String item) {
        when(inboxes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        atFixedTime(() -> service.handle(record(payload("valid".equals(order) ? orderId.toString() : order,
                "valid".equals(item) ? itemId.toString() : item))));
        ArgumentCaptor<OrderInbox> captured = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxes, times(2)).saveAndFlush(captured.capture());
        assertEquals(InboxStatus.FAILED, captured.getValue().getStatus());
        assertEquals("Missing or invalid orderId/orderItemId", captured.getValue().getLastError());
        verifyNoInteractions(bundles);
    }

    @Test
    void unchangedOrMissingOrderItemIsIgnoredInsteadOfReportingNewProcessing() {
        when(inboxes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        atFixedTime(() -> service.handle(record(payload(orderId.toString(), itemId.toString()))));
        ArgumentCaptor<OrderInbox> captured = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxes, times(2)).saveAndFlush(captured.capture());
        assertEquals(InboxStatus.IGNORED, captured.getValue().getStatus());
        assertNull(captured.getValue().getLastError());
    }

    @Test
    void failedBusinessHandlingPropagatesWithoutPersistingProcessedStatus() {
        when(inboxes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        RuntimeException failure = new IllegalStateException("database failure");
        when(bundles.markPacked(orderId, itemId, "PACK-UNIT")).thenThrow(failure);
        atFixedTime(() -> assertSame(failure, assertThrows(RuntimeException.class,
                () -> service.handle(record(payload(orderId.toString(), itemId.toString()))))));
        ArgumentCaptor<OrderInbox> captured = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxes).saveAndFlush(captured.capture());
        assertEquals(InboxStatus.PROCESSING, captured.getValue().getStatus());
        assertNull(captured.getValue().getProcessedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "{\"op\":\"u\",\"after\":{}}", "{\"eventType\":\"UNRELATED_EVENT\"}"})
    void tombstoneUpdatesAndUnrelatedEventsDoNotClaimInbox(String raw) {
        service.handle(record(raw));
        verifyNoInteractions(inboxes, bundles);
    }

    private String payload(String order, String item) {
        return "{\"eventType\":\"CUSTOM_BUNDLE_PACKED\",\"eventId\":\"event-unit\",\"packingId\":\"packing-unit\","
                + "\"packingCode\":\"PACK-UNIT\",\"orderId\":" + jsonString(order) + ",\"orderItemId\":" + jsonString(item) + "}";
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ConsumerRecord<String, String> record(String raw) {
        return new ConsumerRecord<>("inventory.custom-bundle-packed", 1, 7L, "business-key", raw);
    }

    private void atFixedTime(Runnable action) {
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            action.run();
        }
    }
}
