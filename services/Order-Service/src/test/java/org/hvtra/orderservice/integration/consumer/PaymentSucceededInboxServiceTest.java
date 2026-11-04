package org.hvtra.orderservice.integration.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PaymentSucceededInboxServiceTest {

    @Mock private OrderService orderService;
    @Mock private OrderInboxRepository inboxRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentEventPayloadParser parser;
    @Mock private PaymentEventMetadataResolver metadataResolver;

    private PaymentSucceededInboxService service;

    @BeforeEach
    void setUp() {
        service = new PaymentSucceededInboxService(orderService, inboxRepository, orderRepository,
                parser, metadataResolver);
    }

    @Test
    void processedSuccessEventRetriesPaidOrderWithFailedInventory() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEventPayload event = event(orderId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 0, 1, "key", "payload");
        OrderInbox inbox = new OrderInbox();
        inbox.setStatus(InboxStatus.PROCESSED);
        Order order = new Order();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
        when(parser.parseCreateEvent("payload")).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn("evt-1");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", null)).thenReturn(Optional.of(inbox));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        service.handle(record);

        verify(orderService).handlePaymentEvent(event);
    }

    @Test
    void processedSuccessEventDoesNotRepeatConsumedInventory() {
        UUID orderId = UUID.randomUUID();
        PaymentOrderEventPayload event = event(orderId);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 0, 1, "key", "payload");
        OrderInbox inbox = new OrderInbox();
        inbox.setStatus(InboxStatus.PROCESSED);
        Order order = new Order();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
        when(parser.parseCreateEvent("payload")).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn("evt-1");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-1", null)).thenReturn(Optional.of(inbox));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));

        service.handle(record);

        verify(orderService, never()).handlePaymentEvent(any());
    }

    @Test
    void newEventClaimsMetadataAndMarksProcessedOnlyAfterBusinessHandlerReturns() {
        PaymentOrderEventPayload event = event(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        event.setPaymentId("payment-1");
        event.setSagaId("saga-1");
        ConsumerRecord<String, String> record = prepare(event);
        when(metadataResolver.sourceService(record)).thenReturn("payment-service");
        when(metadataResolver.correlationId(record)).thenReturn("correlation-1");
        when(metadataResolver.serializeHeaders(record)).thenReturn("{\"trace\":\"test\"}");
        when(inboxRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> {
            ArgumentCaptor<OrderInbox> claim = ArgumentCaptor.forClass(OrderInbox.class);
            verify(inboxRepository).saveAndFlush(claim.capture());
            assertEquals(InboxStatus.PROCESSING, claim.getValue().getStatus());
            assertNull(claim.getValue().getProcessedAt());
            return null;
        }).when(orderService).handlePaymentEvent(event);
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(now);
            service.handle(record);
        }
        ArgumentCaptor<OrderInbox> claim = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxRepository).saveAndFlush(claim.capture());
        OrderInbox stored = claim.getValue();
        assertEquals(InboxStatus.PROCESSED, stored.getStatus());
        assertEquals(now, stored.getProcessedAt());
        assertEquals("evt-new", stored.getEventId());
        assertEquals("PAYMENT_SUCCEEDED", stored.getEventType());
        assertEquals("PAYMENT", stored.getEntityType());
        assertEquals(event.getPaymentId(), stored.getEntityId());
        assertEquals("payment-service", stored.getSourceService());
        assertEquals(record.value(), stored.getPayloadJson());
        assertEquals(record.partition(), stored.getPartitionNo());
        assertEquals(record.offset(), stored.getOffsetNo());
        assertEquals("correlation-1", stored.getCorrelationId());
        assertNull(stored.getLastError());
        verifyNoInteractions(orderRepository);
    }

    @ParameterizedTest @EnumSource(value = InboxStatus.class, names = {"FAILED", "DEAD", "IGNORED"})
    void terminalFailureOrIgnoredReplayCannotBecomeProcessedOrRepeatSideEffects(InboxStatus state) {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        ConsumerRecord<String, String> record = prepare(event);
        OrderInbox prior = new OrderInbox();
        prior.setStatus(state);
        prior.setLastError("original failure");
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-new", null)).thenReturn(Optional.of(prior));
        service.handle(record);
        assertEquals(state, prior.getStatus());
        assertEquals("original failure", prior.getLastError());
        verify(inboxRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderService, orderRepository);
    }

    @ParameterizedTest @EnumSource(value = InboxStatus.class, names = {"RECEIVED", "PROCESSING"})
    void duplicateInFlightIsRejectedWithoutRunningBusinessHandler(InboxStatus state) {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        ConsumerRecord<String, String> record = prepare(event);
        OrderInbox prior = new OrderInbox();
        prior.setStatus(state);
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-new", null)).thenReturn(Optional.of(prior));
        assertThrows(IllegalStateException.class, () -> service.handle(record));
        assertEquals(state, prior.getStatus());
        verify(inboxRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderService, orderRepository);
    }

    @Test
    void failedBusinessProcessingDoesNotMarkInboxProcessed() {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        ConsumerRecord<String, String> record = prepare(event);
        when(inboxRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        IllegalStateException unavailable = new IllegalStateException("inventory unavailable");
        doThrow(unavailable).when(orderService).handlePaymentEvent(event);
        assertSame(unavailable, assertThrows(IllegalStateException.class, () -> service.handle(record)));
        ArgumentCaptor<OrderInbox> claim = ArgumentCaptor.forClass(OrderInbox.class);
        verify(inboxRepository).saveAndFlush(claim.capture());
        assertEquals(InboxStatus.PROCESSING, claim.getValue().getStatus());
        assertNull(claim.getValue().getProcessedAt());
        verifyNoInteractions(orderRepository);
    }

    @Test
    void duplicateInsertRacePropagatesWithoutInvokingBusinessHandler() {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        ConsumerRecord<String, String> record = prepare(event);
        DataIntegrityViolationException race = new DataIntegrityViolationException("unique event/group");
        when(inboxRepository.saveAndFlush(any())).thenThrow(race);
        assertSame(race, assertThrows(DataIntegrityViolationException.class, () -> service.handle(record)));
        verifyNoInteractions(orderService, orderRepository);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"PAYMENT_FAILED", "PAYMENT_WAITING", "UNKNOWN"})
    void processedNonSuccessReplayNeverRetriesInventory(String type) {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        event.setEventType(type);
        ConsumerRecord<String, String> record = prepare(event);
        OrderInbox prior = new OrderInbox();
        prior.setStatus(InboxStatus.PROCESSED);
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-new", null)).thenReturn(Optional.of(prior));
        service.handle(record);
        assertEquals(InboxStatus.PROCESSED, prior.getStatus());
        verifyNoInteractions(orderService, orderRepository);
    }

    @ParameterizedTest @EnumSource(value = OrderInventoryProcessingStatus.class, names = {"NOT_STARTED", "FAILED", "RESERVED", "CONSUMED"})
    void processedSuccessDoesNotRetryInventoryForAnUnpaidOrder(OrderInventoryProcessingStatus stock) {
        PaymentOrderEventPayload event = event(UUID.randomUUID());
        ConsumerRecord<String, String> record = prepare(event);
        OrderInbox prior = new OrderInbox();
        prior.setStatus(InboxStatus.PROCESSED);
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-new", null)).thenReturn(Optional.of(prior));
        Order order = Order.builder().paymentStatus(PaymentStatus.WAITING_PAYMENT).inventoryProcessingStatus(stock).build();
        when(orderRepository.findByIdAndIsDeletedFalse(UUID.fromString(event.getOrderId()))).thenReturn(Optional.of(order));
        service.handle(record);
        assertEquals(PaymentStatus.WAITING_PAYMENT, order.getPaymentStatus());
        assertEquals(stock, order.getInventoryProcessingStatus());
        verifyNoInteractions(orderService);
        verify(inboxRepository, never()).saveAndFlush(any());
    }

    @Test void successReplayWithoutOrderIdentityCannotRetryInventory() {
        PaymentOrderEventPayload event = event(null);
        ConsumerRecord<String, String> record = prepare(event);
        OrderInbox prior = new OrderInbox();
        prior.setStatus(InboxStatus.PROCESSED);
        when(inboxRepository.findByEventIdAndConsumerGroup("evt-new", null)).thenReturn(Optional.of(prior));
        service.handle(record);
        verifyNoInteractions(orderRepository, orderService);
    }

    @Test void nonCreateOrTombstoneSkipsInboxAndBusiness() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 0, 1, "key", null);
        when(parser.parseCreateEvent(null)).thenReturn(Optional.empty());
        service.handle(record);
        verifyNoInteractions(inboxRepository, metadataResolver, orderService, orderRepository);
    }

    private ConsumerRecord<String, String> prepare(PaymentOrderEventPayload event) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payments", 2, 11, "payment-key", "payload");
        when(parser.parseCreateEvent(record.value())).thenReturn(Optional.of(event));
        when(metadataResolver.resolveEventId(record, event)).thenReturn("evt-new");
        return record;
    }

    private PaymentOrderEventPayload event(UUID orderId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setOrderId(orderId == null ? null : orderId.toString());
        event.setEventType("PAYMENT_SUCCEEDED");
        return event;
    }
}
