package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnRefundTransactionServiceImplTest {

    @Mock
    private OrderReturnRepository orderReturnRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderReturnHistoryWriter orderReturnHistoryWriter;

    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    private OrderReturnRefundTransactionServiceImpl service;

    private UUID orderId;
    private UUID returnId;
    private UUID paymentId;
    private UUID transactionId;
    private Order order;
    private OrderItem orderItem;
    private OrderReturn orderReturn;

    @BeforeEach
    void setUp() {
        service = new OrderReturnRefundTransactionServiceImpl(
                orderReturnRepository,
                orderRepository,
                orderReturnHistoryWriter,
                orderOutboxPublisher
        );

        orderId = UUID.randomUUID();
        returnId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setReturnStatus(OrderReturnStatus.NONE);

        orderItem = new OrderItem();
        orderItem.setId(UUID.randomUUID());
        orderItem.setOrder(order);
        orderItem.setQuantity(new BigDecimal("3.000"));
        orderItem.setIsDeleted(false);
        order.setItems(List.of(orderItem));

        orderReturn = OrderReturn.builder()
                .order(order)
                .status(ReturnStatus.RECEIVED)
                .refundRequired(true)
                .build();
        orderReturn.setId(returnId);

        OrderReturnItem returnItem = OrderReturnItem.builder()
                .orderReturn(orderReturn)
                .orderItem(orderItem)
                .quantity(new BigDecimal("3.000"))
                .condition(ReturnCondition.SEALED)
                .reason(ReturnReason.DEFECTIVE)
                .build();
        returnItem.setId(UUID.randomUUID());
        returnItem.setIsDeleted(false);
        orderReturn.setItems(List.of(returnItem));
    }

    @Test
    void applyRefundSuccessUpdatesOrderAndKeepsReturnStatusReceived() {
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));

        PaymentClient.PaymentRefundResponse paymentRefund = new PaymentClient.PaymentRefundResponse(
                paymentId,
                orderId,
                transactionId,
                returnId,
                new BigDecimal("300000"),
                new BigDecimal("300000"),
                "REFUNDED",
                "CASH",
                LocalDateTime.now()
        );

        OrderReturnRefundResponse response = service.applyRefundSuccess(returnId, paymentRefund, null);

        assertEquals(returnId, response.getReturnId());
        assertEquals(orderId, response.getOrderId());
        assertEquals(paymentId, response.getPaymentId());
        assertEquals(transactionId, response.getRefundTransactionId());
        assertEquals(ReturnStatus.RECEIVED, response.getReturnStatus());
        assertEquals(ReturnStatus.RECEIVED, orderReturn.getStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(OrderReturnStatus.FULLY_RETURNED, order.getReturnStatus());

        verify(orderReturnRepository).save(orderReturn);
        verify(orderRepository).save(order);
        verify(orderReturnHistoryWriter).writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.REFUND_COMPLETED,
                ReturnStatus.RECEIVED,
                ReturnStatus.RECEIVED,
                null,
                "Refund completed",
                "PAYMENT_TRANSACTION",
                transactionId.toString()
        );
        verify(orderOutboxPublisher).publishReturnRefundCompletedNotification(
                eq(orderReturn),
                eq(ReturnStatus.RECEIVED),
                isNull(),
                eq(paymentRefund)
        );
    }

    @Test
    void applyRefundSuccessRejectsInvalidPaymentStatus() {
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));

        PaymentClient.PaymentRefundResponse invalidRefund = new PaymentClient.PaymentRefundResponse(
                paymentId,
                orderId,
                transactionId,
                returnId,
                new BigDecimal("300000"),
                new BigDecimal("0"),
                "FAILED",
                "CASH",
                LocalDateTime.now()
        );

        assertThrows(
                InvalidOrderReturnException.class,
                () -> service.applyRefundSuccess(returnId, invalidRefund, null)
        );
    }
}
