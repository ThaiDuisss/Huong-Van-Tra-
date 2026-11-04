package org.hvtra.orderservice.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommandLine;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOutboxNotificationContractTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock private OutboxEventService<OrderOutboxEvent> events;
    @Mock private OrderStatusHistoryRepository histories;
    private OrderOutboxPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        publisher = new OrderOutboxPublisher(events, new OrderOutboxTopicResolver(), histories);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCEEDED", "FAILED", "NEED_REVIEW", "CANCELLED"})
    void paymentNotificationLinksPayloadHeadersAndIdempotencyToOriginalPaymentEvent(String type) {
        Order order = order();
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setEventId("payment-event");
        event.setEventKey("fallback-key");
        event.setPaymentId("payment-id");
        event.setPaymentCode("PAY-CURRENT");
        atFixedTime(() -> {
            switch (type) {
                case "SUCCEEDED" -> publisher.publishOrderPaymentSucceededNotification(order, event, OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED, "saga");
                case "FAILED" -> publisher.publishOrderPaymentFailedNotification(order, event, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED, "saga", "declined");
                case "NEED_REVIEW" -> publisher.publishOrderPaymentNeedReviewNotification(order, event, OrderStatus.EXPIRED, OrderStatus.EXPIRED, "saga", null);
                case "CANCELLED" -> publisher.publishOrderCancelledNotification(order, event, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, "saga", "cancelled");
                default -> throw new AssertionError(type);
            }
        });
        OutboxSaveCommand saved = savedCommand();
        assertEquals(order.getId().toString(), saved.aggregateId());
        assertEquals(order.getId().toString(), saved.messageKey());
        assertEquals("payment-event", saved.causationId());
        assertEquals("saga", saved.correlationId());
        assertTrue(saved.businessKey().endsWith(":payment-event"));
        assertEquals("notification.order." + ("CANCELLED".equals(type) ? "cancelled" : "payment-" + type.toLowerCase().replace('_', '-')), saved.topic());
        var payload = mapper.valueToTree(saved.payload());
        assertEquals("payment-id", payload.get("paymentId").asText());
        assertEquals("PAY-CURRENT", payload.get("paymentCode").asText());
        assertEquals(order.getCustomerId().toString(), payload.get("customerId").asText());
        assertEquals(saved.eventId(), payload.get("eventId").asText());
        assertEquals(saved.eventType(), payload.get("eventType").asText());
        var headers = mapper.valueToTree(saved.headers());
        assertEquals(saved.eventId(), headers.get("event_id").asText());
        assertEquals("saga", headers.get("saga_id").asText());
        assertEquals(saved.businessKey(), headers.get("businessKey").asText());
        Map<?, ?> rawPayload = assertInstanceOf(Map.class, saved.payload());
        assertEquals(NOW, rawPayload.get("occurredAt"));
    }

    @Test
    void absentPaymentEventUsesOrderPaymentCodeAndStableUnknownCausationWithoutFakePaymentIdentity() {
        Order order = order();
        publisher.publishOrderPaymentFailedNotification(order, null, null, OrderStatus.PAYMENT_FAILED, null, " ");
        OutboxSaveCommand saved = savedCommand();
        var payload = mapper.valueToTree(saved.payload());
        assertNull(saved.causationId());
        assertTrue(saved.businessKey().endsWith(":unknown"));
        assertEquals(order.getId().toString(), saved.correlationId());
        assertEquals("PAY-SNAPSHOT", payload.get("paymentCode").asText());
        assertTrue(payload.get("paymentId").isNull());
        assertTrue(payload.get("oldStatus").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "RECEIVED", "CANCELLED", "REFUND_COMPLETED", "COMPLETED"})
    void returnNotificationsRetainReturnIdentityAndOnlyRefundEventCarriesLedgerIdentifiers(String type) {
        OrderReturn record = record();
        UUID actor = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        record.setRefundRequired(false);
        switch (type) {
            case "CREATED" -> publisher.publishReturnCreatedNotification(record, actor);
            case "RECEIVED" -> publisher.publishReturnReceivedNotification(record, ReturnStatus.CREATED, actor);
            case "CANCELLED" -> publisher.publishReturnCancelledNotification(record, ReturnStatus.CREATED, actor);
            case "REFUND_COMPLETED" -> publisher.publishReturnRefundCompletedNotification(record, ReturnStatus.RECEIVED, actor,
                    new PaymentClient.PaymentRefundResponse(paymentId, record.getOrder().getId(), refundId, record.getId(),
                            BigDecimal.TEN, BigDecimal.TEN, "REFUNDED", "CASH", null));
            case "COMPLETED" -> publisher.publishReturnCompletedNotification(record, ReturnStatus.RECEIVED, actor);
            default -> throw new AssertionError(type);
        }
        OutboxSaveCommand saved = savedCommand();
        var payload = mapper.valueToTree(saved.payload());
        assertEquals("RETURN_" + type, saved.eventType());
        assertEquals(record.getId().toString(), saved.aggregateId());
        assertEquals(record.getId().toString(), saved.messageKey());
        assertEquals(record.getId().toString(), payload.get("returnId").asText());
        assertEquals(record.getOrder().getId().toString(), payload.get("orderId").asText());
        assertEquals(actor.toString(), payload.get("actorUserId").asText());
        assertEquals(actor.toString(), saved.causationId());
        if ("REFUND_COMPLETED".equals(type)) {
            assertEquals(refundId.toString(), payload.get("refundTransactionId").asText());
            assertEquals(paymentId.toString(), payload.get("paymentId").asText());
        } else {
            assertTrue(payload.get("refundTransactionId").isNull());
            assertTrue(payload.get("paymentId").isNull());
        }
        if ("COMPLETED".equals(type)) {
            assertTrue(payload.get("message").asText().contains("Không phát sinh hoàn tiền"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ReturnInventoryDispositionResult.class, names = {"RESTOCK", "DAMAGED", "WRITE_OFF"})
    void dispositionCommandPublishesExactLineIdsQuantityResultAndDestination(ReturnInventoryDispositionResult result) {
        var command = command();
        var item = new OrderReturnItem();
        item.setId(UUID.randomUUID());
        UUID orderItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        var line = OrderReturnInventoryCommandLine.builder().returnItem(item).orderItemId(orderItemId).productVariantId(variantId)
                .quantity(new BigDecimal("1.500")).result(result).destinationLocationId(destination).note("QC fixture").build();
        line.setIsDeleted(false);
        var deleted = new OrderReturnInventoryCommandLine();
        deleted.setIsDeleted(true);
        command.setLines(List.of(line, deleted));
        publisher.publishReturnInventoryDispositionRequested(command, command.getRequestedBy());
        OutboxSaveCommand saved = savedCommand();
        var payload = mapper.valueToTree(saved.payload());
        assertEquals(command.getCommandId().toString(), saved.aggregateId());
        assertEquals(command.getCommandId().toString(), saved.messageKey());
        assertEquals(command.getCommandId().toString(), saved.businessKey());
        assertEquals(command.getCommandId().toString(), payload.get("commandId").asText());
        assertEquals(command.getOrderReturn().getId().toString(), payload.get("returnId").asText());
        assertEquals(1, payload.get("lines").size());
        var publishedLine = payload.get("lines").get(0);
        assertEquals(item.getId().toString(), publishedLine.get("returnItemId").asText());
        assertEquals(orderItemId.toString(), publishedLine.get("orderItemId").asText());
        assertEquals(variantId.toString(), publishedLine.get("productVariantId").asText());
        assertEquals(0, new BigDecimal("1.500").compareTo(publishedLine.get("quantity").decimalValue()));
        assertEquals(result.name(), publishedLine.get("result").asText());
        assertEquals(destination.toString(), publishedLine.get("destinationLocationId").asText());
        assertEquals("order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED", saved.topic());
    }

    @Test
    void orphanRecoveryChangesPublicationKeyPerAttemptWhileKeepingSameCommandAndReturnIdentity() {
        var command = command();
        command.setLines(null);
        command.setRequestedBy(null);
        publisher.publishReturnInventoryDispositionRecovery(command, 1);
        publisher.publishReturnInventoryDispositionRecovery(command, 2);
        ArgumentCaptor<OutboxSaveCommand> saved = ArgumentCaptor.forClass(OutboxSaveCommand.class);
        verify(events, times(2)).saveEventIfAbsent(saved.capture());
        List<OutboxSaveCommand> publications = saved.getAllValues();
        assertEquals(command.getCommandId() + ":recovery:1", publications.getFirst().businessKey());
        assertEquals(command.getCommandId() + ":recovery:2", publications.getLast().businessKey());
        assertNotEquals(publications.getFirst().eventId(), publications.getLast().eventId());
        for (var publication : publications) {
            assertEquals(command.getCommandId().toString(), publication.messageKey());
            var payload = mapper.valueToTree(publication.payload());
            assertEquals(command.getCommandId().toString(), payload.get("commandId").asText());
            assertEquals(command.getOrderReturn().getId().toString(), payload.get("returnId").asText());
            assertTrue(payload.get("lines").isEmpty());
            assertTrue(payload.get("requestedBy").isNull());
        }
    }

    @Test
    void outboxPersistenceFailureIsPropagatedInsteadOfReportingPublicationSuccess() {
        RuntimeException failure = new IllegalStateException("outbox persistence failed");
        doThrow(failure).when(events).saveEventIfAbsent(any());
        var record = record();
        assertSame(failure, assertThrows(RuntimeException.class, () -> publisher.publishReturnCreatedNotification(record, null)));
        verify(events).saveEventIfAbsent(any());
    }

    @Test
    void couponReplayUsesStableBusinessKeyAndDeduplicatesUsageIdsAcrossDiscountLines() {
        Order order = order();
        UUID usage = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OrderDiscount first = new OrderDiscount();
        first.setUsageId(usage);
        OrderDiscount repeated = new OrderDiscount();
        repeated.setUsageId(usage);
        order.setDiscounts(List.of(first, repeated, new OrderDiscount()));
        publisher.publishCouponUsageCommit(order, "first-cause", null);
        publisher.publishCouponUsageCommit(order, "replayed-cause", null);
        ArgumentCaptor<OutboxSaveCommand> saved = ArgumentCaptor.forClass(OutboxSaveCommand.class);
        verify(events, times(2)).saveEventIfAbsent(saved.capture());
        assertEquals(saved.getAllValues().getFirst().businessKey(), saved.getAllValues().getLast().businessKey());
        for (var command : saved.getAllValues()) {
            var payload = mapper.valueToTree(command.payload());
            assertEquals(List.of(usage.toString()), mapper.convertValue(payload.get("usageIds"), List.class));
            assertEquals(order.getId().toString(), command.aggregateId());
        }
        // Persistence uniqueness belongs to Common OutboxEventService, which is mocked here.
    }

    @Test
    void returnNotificationOmitsNullDeletedLinesButPreservesIncompleteCreationSnapshot() {
        OrderReturn record = record();
        record.getOrder().setCustomerId(null);
        record.getOrder().setCreatedBy(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        OrderReturnItem active = new OrderReturnItem();
        active.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        active.setQuantity(new BigDecimal("0.25"));
        active.setNote("Frozen return note");
        OrderReturnItem deleted = new OrderReturnItem();
        deleted.setIsDeleted(true);
        record.setItems(new ArrayList<>(Arrays.asList(null, deleted, active)));
        publisher.publishReturnCreatedNotification(record, null);
        var payload = mapper.valueToTree(savedCommand().payload());
        assertTrue(payload.get("customerId").isNull());
        assertEquals(1, payload.get("items").size());
        var line = payload.get("items").get(0);
        assertEquals(active.getId().toString(), line.get("returnItemId").asText());
        assertEquals(new BigDecimal("0.25"), line.get("quantity").decimalValue());
        assertTrue(line.get("orderItemId").isNull());
        assertTrue(line.get("productVariantId").isNull());
        assertTrue(line.get("condition").isNull());
        assertEquals("Frozen return note", line.get("note").asText());
    }

    private OutboxSaveCommand savedCommand() {
        ArgumentCaptor<OutboxSaveCommand> saved = ArgumentCaptor.forClass(OutboxSaveCommand.class);
        verify(events).saveEventIfAbsent(saved.capture());
        return saved.getValue();
    }

    private Order order() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("ORDER-UNIT");
        order.setCustomerId(UUID.randomUUID());
        order.setPaymentCode("PAY-SNAPSHOT");
        return order;
    }

    private OrderReturn record() {
        OrderReturn record = new OrderReturn();
        record.setId(UUID.randomUUID());
        record.setOrder(order());
        record.setReturnCode("RETURN-UNIT");
        return record;
    }

    private OrderReturnInventoryCommand command() {
        return OrderReturnInventoryCommand.builder().commandId(UUID.randomUUID()).orderReturn(record())
                .requestedBy(UUID.randomUUID()).status(OrderReturnInventoryCommandStatus.PENDING).build();
    }

    private void atFixedTime(Runnable action) {
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            action.run();
        }
    }
}
