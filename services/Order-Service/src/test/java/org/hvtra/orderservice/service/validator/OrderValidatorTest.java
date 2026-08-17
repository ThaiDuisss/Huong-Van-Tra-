package org.hvtra.orderservice.service.validator;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    @Test
    void markPaidKeepsAlreadyPaidExceptionAndMessage() {
        Order order = Order.builder()
                .paymentStatus(PaymentStatus.PAID)
                .finalAmount(new BigDecimal("10.00"))
                .build();

        OrderAlreadyPaidException exception = assertThrows(
                OrderAlreadyPaidException.class,
                () -> validator.validateMarkPaid(order, new BigDecimal("10.00"))
        );

        assertEquals("Đơn hàng đã được thanh toán", exception.getMessage());
    }

    @Test
    void paymentAmountKeepsInsufficientAmountExceptionAndMessage() {
        Order order = Order.builder()
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .finalAmount(new BigDecimal("10.00"))
                .build();

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> validator.validatePaymentAmount(order, new BigDecimal("9.99"))
        );

        assertEquals("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng", exception.getMessage());
    }

    @Test
    void editableDraftChecksStatusBeforeExpiration() {
        Order order = Order.builder()
                .status(OrderStatus.COMPLETED)
                .expiresAt(Instant.EPOCH)
                .build();

        InvalidOrderStatusException exception = assertThrows(
                InvalidOrderStatusException.class,
                () -> validator.validateEditableDraft(order, Instant.now())
        );

        assertEquals("ORDER_NOT_DRAFT", exception.getMessage());
    }

    @Test
    void positiveQuantityKeepsValidationMessage() {
        InvalidOrderStatusException exception = assertThrows(
                InvalidOrderStatusException.class,
                () -> validator.requirePositiveQuantity(BigDecimal.ZERO)
        );

        assertEquals("Số lượng phải lớn hơn 0", exception.getMessage());
    }
}
