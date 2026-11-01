package org.hvtra.orderservice.outbox.service;

import org.hvtra.commonservice.common_core.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.common_core.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.outbox.event.OrderEventType;
import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOutboxPublisherTest {

    @Mock
    private OutboxEventService<OrderOutboxEvent> orderOutboxEventService;
    @Mock
    private OrderStatusHistoryRepository historyRepository;

    private OrderOutboxTopicResolver orderOutboxTopicResolver;

    private OrderOutboxPublisher publisher;

    @Captor
    private ArgumentCaptor<OutboxSaveCommand> commandCaptor;

    @BeforeEach
    void setUp() {
        orderOutboxTopicResolver = new OrderOutboxTopicResolver();
        publisher = new OrderOutboxPublisher(orderOutboxEventService, orderOutboxTopicResolver, historyRepository);
        lenient().when(historyRepository.findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(
                any(), eq(OrderStatus.COMPLETED))).thenReturn(Optional.of(OrderStatusHistory.builder()
                .changedAt(Instant.parse("2026-10-01T08:00:00Z")).build()));
    }

    @Test
    @DisplayName("ORDER_COMPLETED payload includes items and customerId when order has customer")
    void publishPaymentCompletedOrchestration_IncludesItemsAndCustomerId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId1 = UUID.randomUUID();
        UUID variantId2 = UUID.randomUUID();

        Order order = Order.builder()
                .orderCode("ORD-1001")
                .customerId(customerId)
                .customerNameSnapshot("Nguyễn Văn A")
                .customerEmailSnapshot("khach@example.com")
                .customerPhoneSnapshot("0900000001")
                .cashierNameSnapshot("Thu ngân 1")
                .status(OrderStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID)
                .originalAmount(new BigDecimal("250000.00"))
                .discountAmount(new BigDecimal("10000.00"))
                .finalAmount(new BigDecimal("240000.00"))
                .paidAmount(new BigDecimal("240000.00"))
                .build();
        order.setId(orderId);

        OrderItem item1 = OrderItem.builder()
                .order(order)
                .productVariantId(variantId1)
                .skuSnapshot("SKU-TRA-OLONG-100G")
                .productNameSnapshot("Trà Ô Long Thuần")
                .variantNameSnapshot("Gói 100g")
                .quantity(new BigDecimal("2.000"))
                .originalUnitPrice(new BigDecimal("80000.00"))
                .actualUnitPrice(new BigDecimal("80000.00"))
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("160000.00"))
                .build();
        item1.setId(UUID.randomUUID());

        OrderItem item2 = OrderItem.builder()
                .order(order)
                .productVariantId(variantId2)
                .skuSnapshot("SKU-TRA-SEN-100G")
                .productNameSnapshot("Trà Sen Tây Hồ")
                .variantNameSnapshot("Hộp 100g")
                .quantity(new BigDecimal("1.000"))
                .originalUnitPrice(new BigDecimal("90000.00"))
                .actualUnitPrice(new BigDecimal("80000.00"))
                .discountAmount(new BigDecimal("10000.00"))
                .lineTotal(new BigDecimal("80000.00"))
                .build();
        item2.setId(UUID.randomUUID());

        order.setItems(List.of(item1, item2));

        publisher.publishPaymentCompletedOrchestration(order, "PAY-EVENT-01", "SAGA-01");

        verify(orderOutboxEventService, atLeastOnce()).saveEventIfAbsent(commandCaptor.capture());

        List<OutboxSaveCommand> captured = commandCaptor.getAllValues();
        OutboxSaveCommand orderCompletedCommand = captured.stream()
                .filter(cmd -> OrderEventType.ORDER_COMPLETED.equals(cmd.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ORDER_COMPLETED event was not published"));

        assertEquals("order.completed", orderCompletedCommand.topic());
        assertEquals(orderId.toString() + ":order-completed", orderCompletedCommand.businessKey());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) orderCompletedCommand.payload();
        assertEquals(orderId.toString(), payload.get("orderId"));
        assertEquals("ORD-1001", payload.get("orderCode"));
        assertEquals(customerId.toString(), payload.get("customerId"));
        assertEquals("COMPLETED", payload.get("orderStatus"));
        assertEquals("PAID", payload.get("paymentStatus"));
        assertNotNull(payload.get("occurredAt"));
        assertEquals(1, payload.get("eventVersion"));
        assertEquals("order-service", payload.get("sourceService"));
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), payload.get("completedAt"));
        // Thông tin dùng cho hóa đơn gửi email
        assertEquals("Nguyễn Văn A", payload.get("customerName"));
        assertEquals("khach@example.com", payload.get("customerEmail"));
        assertEquals("0900000001", payload.get("customerPhone"));
        assertEquals("Thu ngân 1", payload.get("cashierName"));
        assertEquals(new BigDecimal("250000.00"), payload.get("originalAmount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertNotNull(items);
        assertEquals(2, items.size());

        Map<String, Object> firstItem = items.get(0);
        assertEquals(variantId1.toString(), firstItem.get("productVariantId"));
        assertEquals("PRODUCT", firstItem.get("purchaseType"));
        assertEquals("SKU-TRA-OLONG-100G", firstItem.get("sku"));
        assertEquals("Trà Ô Long Thuần", firstItem.get("productName"));
        assertEquals(new BigDecimal("2.000"), firstItem.get("quantity"));
        assertEquals(new BigDecimal("80000.00"), firstItem.get("unitPrice"));
        assertEquals(new BigDecimal("160000.00"), firstItem.get("lineTotal"));

        Map<String, Object> secondItem = items.get(1);
        assertEquals(variantId2.toString(), secondItem.get("productVariantId"));
        assertEquals("SKU-TRA-SEN-100G", secondItem.get("sku"));
        assertEquals("Trà Sen Tây Hồ", secondItem.get("productName"));
        assertEquals(new BigDecimal("1.000"), secondItem.get("quantity"));
        assertEquals(new BigDecimal("80000.00"), secondItem.get("unitPrice"));
        assertEquals(new BigDecimal("80000.00"), secondItem.get("lineTotal"));
    }

    @Test
    @DisplayName("ORDER_COMPLETED preserves null customerId for guest retail orders")
    void publishPaymentCompletedOrchestration_GuestOrderHasNullCustomerId() {
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .orderCode("ORD-GUEST-01")
                .customerId(null)
                .customerNameSnapshot("Khách lẻ")
                .status(OrderStatus.COMPLETED)
                .paymentStatus(PaymentStatus.PAID)
                .originalAmount(new BigDecimal("50000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("50000.00"))
                .paidAmount(new BigDecimal("50000.00"))
                .build();
        order.setId(orderId);

        OrderItem item = OrderItem.builder()
                .order(order)
                .productVariantId(UUID.randomUUID())
                .skuSnapshot("SKU-GUEST")
                .productNameSnapshot("Trà Đá")
                .quantity(new BigDecimal("1.000"))
                .originalUnitPrice(new BigDecimal("50000.00"))
                .actualUnitPrice(new BigDecimal("50000.00"))
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("50000.00"))
                .build();
        item.setId(UUID.randomUUID());
        order.setItems(List.of(item));

        publisher.publishPaymentCompletedOrchestration(order, "PAY-EVENT-02");

        verify(orderOutboxEventService, atLeastOnce()).saveEventIfAbsent(commandCaptor.capture());

        OutboxSaveCommand orderCompletedCommand = commandCaptor.getAllValues().stream()
                .filter(cmd -> OrderEventType.ORDER_COMPLETED.equals(cmd.eventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ORDER_COMPLETED event was not published"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) orderCompletedCommand.payload();
        assertNull(payload.get("customerId"), "Guest order must have null customerId, not faked or replaced");
        assertNotNull(payload.get("items"));
    }

    @Test
    @DisplayName("RETURN_COMPLETED includes return items with returnReason from ReturnItem entity")
    void publishReturnCompletedNotification_IncludesItemsAndReturnReason() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        Order order = Order.builder()
                .orderCode("ORD-2001")
                .customerId(customerId)
                .build();
        order.setId(orderId);

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productVariantId(variantId)
                .skuSnapshot("SKU-TRA-DEN-100G")
                .productNameSnapshot("Trà Đen Cổ Thụ")
                .quantity(new BigDecimal("2.000"))
                .actualUnitPrice(new BigDecimal("120000.00"))
                .lineTotal(new BigDecimal("240000.00"))
                .build();
        orderItem.setId(UUID.randomUUID());

        OrderReturn orderReturn = OrderReturn.builder()
                .returnCode("RET-2001")
                .order(order)
                .status(ReturnStatus.COMPLETED)
                .refundRequired(true)
                .build();
        orderReturn.setId(returnId);

        OrderReturnItem returnItem = OrderReturnItem.builder()
                .orderReturn(orderReturn)
                .orderItem(orderItem)
                .quantity(new BigDecimal("1.000"))
                .reason(ReturnReason.CHANGE_OF_MIND)
                .condition(ReturnCondition.SEALED)
                .note("Khách đổi ý muốn lấy loại khác")
                .build();
        returnItem.setId(UUID.randomUUID());

        orderReturn.setItems(List.of(returnItem));

        UUID actorUserId = UUID.randomUUID();
        publisher.publishReturnCompletedNotification(orderReturn, ReturnStatus.RECEIVED, actorUserId);

        verify(orderOutboxEventService).saveEventIfAbsent(commandCaptor.capture());

        OutboxSaveCommand command = commandCaptor.getValue();
        assertEquals(OrderEventType.RETURN_COMPLETED, command.eventType());
        assertEquals("notification.return.completed", command.topic());
        assertEquals(returnId.toString() + ":return-completed", command.businessKey());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) command.payload();
        assertEquals(returnId.toString(), payload.get("returnId"));
        assertEquals("RET-2001", payload.get("returnCode"));
        assertEquals(orderId.toString(), payload.get("orderId"));
        assertEquals(customerId.toString(), payload.get("customerId"));
        assertEquals("COMPLETED", payload.get("newStatus"));
        assertNotNull(payload.get("occurredAt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertNotNull(items);
        assertEquals(1, items.size());

        Map<String, Object> itemPayload = items.get(0);
        assertEquals(returnItem.getId().toString(), itemPayload.get("returnItemId"));
        assertEquals(orderItem.getId().toString(), itemPayload.get("orderItemId"));
        assertEquals(variantId.toString(), itemPayload.get("productVariantId"));
        assertEquals("SKU-TRA-DEN-100G", itemPayload.get("sku"));
        assertEquals("Trà Đen Cổ Thụ", itemPayload.get("productName"));
        assertEquals(new BigDecimal("1.000"), itemPayload.get("quantity"));
        assertEquals("CHANGE_OF_MIND", itemPayload.get("returnReason"));
        assertEquals("SEALED", itemPayload.get("condition"));
        assertEquals("Khách đổi ý muốn lấy loại khác", itemPayload.get("note"));
    }
}
