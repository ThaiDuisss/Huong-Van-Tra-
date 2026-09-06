package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderReturnItemRepository orderReturnItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    private OrderReturnServiceImpl service;

    private UUID orderId;
    private UUID orderItemId;
    private Order order;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        service = new OrderReturnServiceImpl(
                orderRepository,
                orderReturnRepository,
                orderReturnItemRepository,
                orderStatusHistoryRepository,
                orderCodeGenerator,
                new OrderReturnMapper()
        );
        orderId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
        order = order(OrderStatus.COMPLETED, PaymentStatus.PAID);
        orderItem = orderItem(order, orderItemId, new BigDecimal("3.000"));
        order.setItems(List.of(orderItem));
    }

    @Test
    void createReturnCompletedPaidOrderSucceeds() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET202609060001");
        when(orderReturnRepository.existsByReturnCode("RET202609060001")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
        );

        assertEquals("RET202609060001", response.getReturnCode());
        assertEquals(orderId, response.getOrderId());
        assertEquals(ReturnStatus.CREATED, response.getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("1.000"), response.getItems().getFirst().getQuantity());

        ArgumentCaptor<OrderReturn> captor = ArgumentCaptor.forClass(OrderReturn.class);
        verify(orderReturnRepository).save(captor.capture());
        assertEquals(ReturnStatus.CREATED, captor.getValue().getStatus());
        assertSame(orderItem, captor.getValue().getItems().getFirst().getOrderItem());
    }

    @Test
    void createReturnRejectsPendingPaymentOrder() {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order is not eligible for return", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsCompletedUnpaidOrder() {
        order.setPaymentStatus(PaymentStatus.UNPAID);
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order is not eligible for return", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsOrderItemFromAnotherOrder() {
        stubEligibleOrderOnly();

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(UUID.randomUUID(), "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Order item does not belong to order", exception.getMessage());
        verify(orderReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsZeroQuantity() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "0.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return quantity must be greater than 0", exception.getMessage());
    }

    @Test
    void createReturnRejectsQuantityGreaterThanPurchasedQuantity() {
        stubEligibleOrder(BigDecimal.ZERO);

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "4.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
    }

    @Test
    void createReturnAllowsPartialReturn() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-PARTIAL");
        when(orderReturnRepository.existsByReturnCode("RET-PARTIAL")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals(new BigDecimal("2.000"), response.getItems().getFirst().getQuantity());
        assertEquals(ReturnReason.QUALITY_ISSUE, response.getItems().getFirst().getReason());
    }

    @Test
    void createReturnSecondReturnCannotExceedRemainingQuantity() {
        stubEligibleOrder(new BigDecimal("2.000"));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "2.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Return quantity exceeds remaining returnable quantity", exception.getMessage());
    }

    @Test
    void createReturnAllowsChangeOfMindWithSealedCondition() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-SEALED");
        when(orderReturnRepository.existsByReturnCode("RET-SEALED")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
        );

        assertEquals(ReturnReason.CHANGE_OF_MIND, response.getItems().getFirst().getReason());
        assertEquals(ReturnCondition.SEALED, response.getItems().getFirst().getCondition());
    }

    @Test
    void createReturnRejectsChangeOfMindWithOpenedCondition() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.OPENED, null)
                )
        );

        assertEquals("Change-of-mind return requires sealed product", exception.getMessage());
    }

    @Test
    void createReturnAllowsQualityIssueWithOpenedCondition() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-QUALITY");
        when(orderReturnRepository.existsByReturnCode("RET-QUALITY")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null)
        );

        assertEquals(ReturnCondition.OPENED, response.getItems().getFirst().getCondition());
    }

    @Test
    void createReturnRejectsOtherWithoutNote() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.OTHER, ReturnCondition.SEALED, " ")
                )
        );

        assertEquals("Note is required when reason is OTHER", exception.getMessage());
    }

    @Test
    void createReturnAllowsOtherWithNote() {
        stubEligibleOrder(BigDecimal.ZERO);
        when(orderCodeGenerator.generateReturnCode()).thenReturn("RET-OTHER");
        when(orderReturnRepository.existsByReturnCode("RET-OTHER")).thenReturn(false);
        when(orderReturnRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnResponse response = service.createReturn(
                orderId,
                request(orderItemId, "1.000", ReturnReason.OTHER, ReturnCondition.SEALED, "Khách báo lý do khác")
        );

        assertEquals("Khách báo lý do khác", response.getItems().getFirst().getNote());
    }

    @Test
    void createReturnRejectsExpiredReturnWindow() {
        stubEligibleOrderOnly();
        stubCompletedAt(Instant.now().minusSeconds(15 * 24 * 60 * 60));

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(
                        orderId,
                        request(orderItemId, "1.000", ReturnReason.CHANGE_OF_MIND, ReturnCondition.SEALED, null)
                )
        );

        assertEquals("Return window has expired", exception.getMessage());
    }

    @Test
    void createReturnRejectsDuplicateItemToAvoidOverReturnAcrossRequestLines() {
        stubEligibleOrderOnly();
        CreateOrderReturnRequest request = CreateOrderReturnRequest.builder()
                .items(List.of(
                        item(orderItemId, "1.000", ReturnReason.QUALITY_ISSUE, ReturnCondition.OPENED, null),
                        item(orderItemId, "1.000", ReturnReason.DAMAGED, ReturnCondition.DAMAGED, null)
                ))
                .build();

        InvalidOrderReturnException exception = assertThrows(
                InvalidOrderReturnException.class,
                () -> service.createReturn(orderId, request)
        );

        assertEquals("Duplicate order item in return request", exception.getMessage());
    }

    private void stubEligibleOrder(BigDecimal returnedQuantity) {
        stubEligibleOrder(returnedQuantity, Instant.now().minusSeconds(60));
    }

    private void stubEligibleOrder(BigDecimal returnedQuantity, Instant completedAt) {
        stubEligibleOrderOnly();
        stubCompletedAt(completedAt);
        stubReturnedQuantity(returnedQuantity);
    }

    private void stubEligibleOrderOnly() {
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    }

    private void stubCompletedAt(Instant completedAt) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .toStatus(OrderStatus.COMPLETED)
                .changedAt(completedAt)
                .build();
        when(orderStatusHistoryRepository.findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(
                orderId,
                OrderStatus.COMPLETED
        )).thenReturn(Optional.of(history));
    }

    private void stubReturnedQuantity(BigDecimal returnedQuantity) {
        when(orderReturnItemRepository.sumReturnedQuantity(eq(orderItemId), any())).thenReturn(returnedQuantity);
    }

    private CreateOrderReturnRequest request(
            UUID itemId,
            String quantity,
            ReturnReason reason,
            ReturnCondition condition,
            String note
    ) {
        return CreateOrderReturnRequest.builder()
                .items(List.of(item(itemId, quantity, reason, condition, note)))
                .build();
    }

    private CreateOrderReturnItemRequest item(
            UUID itemId,
            String quantity,
            ReturnReason reason,
            ReturnCondition condition,
            String note
    ) {
        return CreateOrderReturnItemRequest.builder()
                .orderItemId(itemId)
                .quantity(new BigDecimal(quantity))
                .reason(reason)
                .condition(condition)
                .note(note)
                .build();
    }

    private Order order(OrderStatus status, PaymentStatus paymentStatus) {
        Order value = Order.builder()
                .orderCode("ORD-001")
                .status(status)
                .paymentStatus(paymentStatus)
                .build();
        value.setId(orderId);
        value.setIsDeleted(false);
        return value;
    }

    private OrderItem orderItem(Order parent, UUID itemId, BigDecimal quantity) {
        OrderItem value = OrderItem.builder()
                .order(parent)
                .productVariantId(UUID.randomUUID())
                .skuSnapshot("SKU-001")
                .productNameSnapshot("Tra sen")
                .variantNameSnapshot("Hop 100g")
                .quantity(quantity)
                .build();
        value.setId(itemId);
        value.setIsDeleted(false);
        return value;
    }
}
