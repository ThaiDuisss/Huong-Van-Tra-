package org.hvtra.orderservice.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.commonservice.common_core.kafka.outbox.repository.BaseOutboxEventRepository;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.hvtra.orderservice.outbox.factory.OrderOutboxEventFactory;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductCompletionContractTest {
    private final OutboxEventService<OrderOutboxEvent> outbox = mock(OutboxEventService.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final Map<String, OutboxSaveCommand> stored = new LinkedHashMap<>();
    private final OrderOutboxPublisher publisher = new OrderOutboxPublisher(outbox, new OrderOutboxTopicResolver(), history);
    private JsonNode fixture;
    private Order order;

    @BeforeEach
    void prepare() throws Exception {
        try (var input = getClass().getResourceAsStream("/contracts/product-completed-v1.json")) {
            fixture = new ObjectMapper().readTree(input);
        }
        order = Order.builder().status(OrderStatus.PAYMENT_CONFIRMED).paymentStatus(PaymentStatus.PAID)
                .customerId(UUID.fromString(fixture.get("customerId").asText())).build();
        order.setId(UUID.fromString(fixture.get("orderId").asText()));
        OrderItem item = OrderItem.builder().order(order)
                .productVariantId(UUID.fromString(fixture.get("items").get(0).get("productVariantId").asText()))
                .skuSnapshot("FIXTURE-TEA").quantity(new BigDecimal("2.000"))
                .actualUnitPrice(new BigDecimal("100.00")).build();
        item.setId(UUID.fromString(fixture.get("items").get(0).get("orderItemId").asText()));
        order.setItems(List.of(item));
        lenient().when(history.findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(any(), any()))
                .thenReturn(Optional.of(OrderStatusHistory.builder()
                        .changedAt(Instant.parse(fixture.get("completedAt").asText())).build()));
        lenient().doAnswer(invocation -> {
            OutboxSaveCommand command = invocation.getArgument(0);
            stored.putIfAbsent(command.businessKey(), command);
            return null;
        }).when(outbox).saveEventIfAbsent(any());
    }

    @Test
    void earlyPaymentDoesNotClaimKeyAndLaterCompletionIsOneImmutableLogicalEvent() {
        publisher.publishPaymentCompletedOrchestration(order, "payment");
        assertFalse(stored.containsKey(order.getId() + ":order-completed"));
        order.setStatus(OrderStatus.COMPLETED);
        publisher.publishOrderCompleted(order, "handover", null);
        var original = stored.get(order.getId() + ":order-completed");
        publisher.publishOrderCompleted(order, "retry", null);
        assertSame(original, stored.get(order.getId() + ":order-completed"));
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        publisher.publishOrderCompleted(order, "refund", null);
        assertSame(original, stored.get(order.getId() + ":order-completed"));
        Map<?, ?> payload = (Map<?, ?>) original.payload();
        assertEquals("PAID", payload.get("paymentStatus"));
        assertEquals(1, payload.get("eventVersion"));
        assertEquals("order-service", payload.get("sourceService"));
        assertEquals(Instant.parse(fixture.get("completedAt").asText()), payload.get("completedAt"));
        Map<?, ?> line = (Map<?, ?>) ((List<?>) payload.get("items")).get(0);
        assertEquals(fixture.get("items").get(0).get("orderItemId").asText(), line.get("orderItemId"));
        assertEquals(fixture.get("items").get(0).get("productVariantId").asText(), line.get("productVariantId"));
        assertEquals("PRODUCT", line.get("purchaseType"));
        assertFalse(line.containsKey("productId"));
    }

    @Test
    void noCompletionForAnyNonCompletedOrNonPaidState() {
        for (OrderStatus status : OrderStatus.values()) {
            if (status == OrderStatus.COMPLETED) continue;
            order.setStatus(status);
            publisher.publishOrderCompleted(order, null, null);
        }
        order.setStatus(OrderStatus.COMPLETED);
        for (PaymentStatus payment : PaymentStatus.values()) {
            if (payment == PaymentStatus.PAID) continue;
            order.setPaymentStatus(payment);
            publisher.publishOrderCompleted(order, null, null);
        }
        verifyNoInteractions(outbox);
    }

    @Test
    void offlineCompletionUsesRecordedSaleTimeAndEmissionRemainsSeparate() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setOfflineSale(true);
        order.setSoldAt(Instant.parse("2026-09-30T08:00:00Z"));
        publisher.publishOrderCompleted(order, null, null);
        Map<?, ?> payload = (Map<?, ?>) stored.values().iterator().next().payload();
        assertEquals(order.getSoldAt(), payload.get("completedAt"));
        assertTrue(((Instant) payload.get("occurredAt")).isAfter(order.getSoldAt()));
        verifyNoInteractions(history);
    }

    @Test
    void invalidLinesAndMissingCompletionHistoryFailBeforeOutboxWrite() {
        order.setStatus(OrderStatus.COMPLETED);
        order.getItems().get(0).setQuantity(BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCompleted(order, null, null));
        order.getItems().get(0).setQuantity(BigDecimal.ONE);
        when(history.findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCompleted(order, null, null));
        verifyNoInteractions(outbox);
    }

    @Test
    void emptyOrUnpersistedLinesCannotCreateCompletionPayload() {
        order.setStatus(OrderStatus.COMPLETED);
        OrderItem item = order.getItems().get(0);
        order.setItems(List.of());
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCompleted(order, null, null));
        order.setItems(List.of(item));
        item.setId(null);
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCompleted(order, null, null));
        item.setId(UUID.randomUUID());
        item.setProductVariantId(null);
        assertThrows(IllegalStateException.class, () -> publisher.publishOrderCompleted(order, null, null));
        verifyNoInteractions(outbox);
    }

    @Test
    void actualOutboxServiceAndFactoryKeepOneSerializedSnapshotOnRetryAndRefund() throws Exception {
        BaseOutboxEventRepository<OrderOutboxEvent> repository = mock(BaseOutboxEventRepository.class);
        Map<String, OrderOutboxEvent> rows = new LinkedHashMap<>();
        when(repository.existsByEventTypeAndBusinessKey(anyString(), anyString()))
                .thenAnswer(call -> rows.containsKey(call.getArgument(1)));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            OrderOutboxEvent row = call.getArgument(0);
            rows.put(row.getBusinessKey(), row);
            return row;
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var actual = new OrderOutboxPublisher(new OutboxEventService<>(repository, new OrderOutboxEventFactory(mapper)),
                new OrderOutboxTopicResolver(), history);
        actual.publishOrderCompleted(order, "early", null);
        assertTrue(rows.isEmpty());
        order.setStatus(OrderStatus.COMPLETED);
        actual.publishOrderCompleted(order, "handover", null);
        String original = rows.values().iterator().next().getPayload();
        actual.publishOrderCompleted(order, "retry", null);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        actual.publishOrderCompleted(order, "refund", null);
        assertEquals(1, rows.size());
        assertEquals(original, rows.values().iterator().next().getPayload());
        assertEquals("PAID", mapper.readTree(original).get("paymentStatus").asText());
        assertEquals(fixture.get("completedAt"), mapper.readTree(original).get("completedAt"));
        verify(repository, times(1)).saveAndFlush(any());
    }
}
