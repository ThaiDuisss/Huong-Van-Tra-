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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void applyRefundSuccessUpdatesOrderAndCompletesReturn() {
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
        assertEquals(ReturnStatus.COMPLETED, response.getReturnStatus());
        assertEquals(ReturnStatus.COMPLETED, orderReturn.getStatus());
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(OrderReturnStatus.FULLY_RETURNED, order.getReturnStatus());

        verify(orderReturnRepository).save(orderReturn);
        verify(orderRepository).save(order);
        verify(orderReturnHistoryWriter).writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.REFUND_COMPLETED,
                ReturnStatus.RECEIVED,
                ReturnStatus.COMPLETED,
                null,
                "Refund completed",
                "PAYMENT_TRANSACTION",
                transactionId.toString()
        );
        verify(orderOutboxPublisher).publishReturnRefundCompletedNotification(
                eq(orderReturn),
                eq(ReturnStatus.COMPLETED),
                isNull(),
                eq(paymentRefund)
        );
        verify(orderOutboxPublisher).publishReturnCompletedNotification(
                eq(orderReturn),
                eq(ReturnStatus.RECEIVED),
                isNull()
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

    @Test
    void serializedRepeatedSuccessPublishesCompletionOnlyOnce() {
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));
        PaymentClient.PaymentRefundResponse paymentRefund = new PaymentClient.PaymentRefundResponse(
                paymentId, orderId, transactionId, returnId, new BigDecimal("300000"),
                new BigDecimal("300000"), "REFUNDED", "CASH", null);
        var first = service.applyRefundSuccess(returnId, paymentRefund, null);
        var second = service.applyRefundSuccess(returnId, paymentRefund, null);
        assertEquals(first.getRefundTransactionId(), second.getRefundTransactionId());
        assertEquals(first.getTotalAmountRefunded(), second.getTotalAmountRefunded());
        verify(orderReturnRepository, times(1)).save(orderReturn);
        verify(orderOutboxPublisher, times(1)).publishReturnRefundCompletedNotification(
                eq(orderReturn), eq(ReturnStatus.COMPLETED), isNull(), eq(paymentRefund));
        verify(orderOutboxPublisher, times(1)).publishReturnCompletedNotification(
                eq(orderReturn), eq(ReturnStatus.RECEIVED), isNull());
        verify(orderOutboxPublisher, never()).publishReturnCompletedNotification(
                eq(orderReturn), eq(ReturnStatus.COMPLETED), isNull());
    }

    @Test
    void latePartialResponseCannotDowngradeAlreadyFullyRefundedOrder() {
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId))
                .thenReturn(List.of(orderReturn));
        var response = service.applyRefundSuccess(returnId, new PaymentClient.PaymentRefundResponse(
                paymentId, orderId, transactionId, returnId, new BigDecimal("100000"),
                new BigDecimal("100000"), "PARTIALLY_REFUNDED", "CASH", null), null);
        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(PaymentStatus.REFUNDED, response.getOrderPaymentStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "PAID", "UNPAID", "WAITING_PAYMENT", "NOT_A_STATUS", "refunded"})
    void rejectsUnsettledOrMalformedPaymentStatusBeforeLockingOrder(String status) {
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        var refund = refund(paymentId, orderId, transactionId, returnId, status);
        assertThrows(InvalidOrderReturnException.class, () -> service.applyRefundSuccess(returnId, refund, null));
        assertEquals(ReturnStatus.RECEIVED, orderReturn.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        verifyNoInteractions(orderRepository, orderReturnHistoryWriter, orderOutboxPublisher);
        verify(orderReturnRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "transaction", "order", "return"})
    void rejectsRefundWithMissingOrMismatchedIdentityWithoutWriting(String mismatch) {
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        var refund = refund("payment".equals(mismatch) ? null : paymentId,
                "order".equals(mismatch) ? UUID.randomUUID() : orderId,
                "transaction".equals(mismatch) ? null : transactionId,
                "return".equals(mismatch) ? UUID.randomUUID() : returnId, "REFUNDED");
        InvalidOrderReturnException failure = assertThrows(InvalidOrderReturnException.class,
                () -> service.applyRefundSuccess(returnId, refund, null));
        assertEquals("Payment refund record does not match this return", failure.getMessage());
        assertEquals(ReturnStatus.RECEIVED, orderReturn.getStatus());
        verifyNoInteractions(orderRepository, orderReturnHistoryWriter, orderOutboxPublisher);
        verify(orderReturnRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReturnStatus.class, names = {"CREATED", "CANCELLED"})
    void refusesSettlementForReturnThatHasNotBeenReceived(ReturnStatus status) {
        orderReturn.setStatus(status);
        when(orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)).thenReturn(Optional.of(orderReturn));
        assertThrows(InvalidOrderReturnException.class, () -> service.applyRefundSuccess(returnId,
                refund(paymentId, orderId, transactionId, returnId, "REFUNDED"), null));
        assertEquals(status, orderReturn.getStatus());
        verifyNoInteractions(orderRepository, orderReturnHistoryWriter, orderOutboxPublisher);
    }

    private PaymentClient.PaymentRefundResponse refund(UUID payment, UUID refundOrder, UUID transaction, UUID refundReturn, String status) {
        return new PaymentClient.PaymentRefundResponse(payment, refundOrder, transaction, refundReturn,
                new BigDecimal("300000"), new BigDecimal("300000"), status, "CASH",
                LocalDateTime.of(2026, 11, 3, 7, 0));
    }
}
