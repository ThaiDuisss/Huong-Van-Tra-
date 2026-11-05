package org.hvtra.orderservice.service.validator;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1", "0"})
    void serviceQuantityGuardRejectsNullZeroAndNegative(String value) {
        assertThrows(InvalidOrderStatusException.class,
                () -> validator.requirePositiveQuantity(value == null ? null : new BigDecimal(value)));
    }

    @Test
    void positiveQuantityPreservesDecimalPrecisionSupportedByDomain() {
        assertEquals(new BigDecimal("1.500"), validator.requirePositiveQuantity(new BigDecimal("1.500")));
    }

    @Test
    void expirationBoundaryIsStrictlyBeforeNowAndNullExpiryIsEditable() {
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Order draft = Order.builder().status(OrderStatus.DRAFT).expiresAt(now).build();
        assertDoesNotThrow(() -> validator.validateEditableDraft(draft, now));
        draft.setExpiresAt(now.minusNanos(1));
        assertEquals("ORDER_EXPIRED", assertThrows(InvalidOrderStatusException.class,
                () -> validator.validateEditableDraft(draft, now)).getMessage());
        draft.setExpiresAt(null);
        assertDoesNotThrow(() -> validator.validateEditableDraft(draft, now));
    }

    @ParameterizedTest
    @CsvSource({"PENDING_PAYMENT,PAID", "COMPLETED,UNPAID", "DRAFT,PARTIALLY_PAID", "CANCELLED,UNPAID"})
    void cancellationRejectsPaidDepositCompletedAndAlreadyCancelledOrders(OrderStatus status, PaymentStatus payment) {
        Order order = Order.builder().status(status).paymentStatus(payment).build();
        assertThrows(InvalidOrderStatusException.class, () -> validator.validateCancellation(order));
    }

    @Test
    void draftCancellationAndEmptyOrderGuardsDoNotBlockValidDrafts() {
        Order draft = Order.builder().status(OrderStatus.DRAFT).paymentStatus(PaymentStatus.UNPAID).build();
        assertDoesNotThrow(() -> validator.validateDraftCancellation(draft));
        assertDoesNotThrow(() -> validator.validateCancellation(draft));
        draft.setStatus(OrderStatus.PENDING_PAYMENT);
        assertThrows(InvalidOrderStatusException.class, () -> validator.validateDraftCancellation(draft));
        draft.setItems(null);
        assertThrows(InvalidOrderStatusException.class, () -> validator.validateDraftHasItems(draft));
        draft.setItems(List.of());
        assertThrows(InvalidOrderStatusException.class, () -> validator.validateDraftHasItems(draft));
        draft.setItems(List.of(new OrderItem()));
        assertDoesNotThrow(() -> validator.validateDraftHasItems(draft));
    }

    @Test
    void cashRequiresEnoughMoneyWhileTransferWaitsForProviderConfirmation() {
        Order order = Order.builder().finalAmount(new BigDecimal("100")).paymentStatus(PaymentStatus.UNPAID).build();
        assertThrows(InvalidPaymentAmountException.class, () -> validator.validateCashPaymentAmount(order, PaymentMethod.CASH, null));
        assertThrows(InvalidPaymentAmountException.class,
                () -> validator.validateCashPaymentAmount(order, PaymentMethod.CASH, new BigDecimal("99.99")));
        assertDoesNotThrow(() -> validator.validateCashPaymentAmount(order, PaymentMethod.CASH, new BigDecimal("100")));
        assertDoesNotThrow(() -> validator.validateCashPaymentAmount(order, PaymentMethod.BANK_TRANSFER, null));
        assertDoesNotThrow(() -> validator.validateMarkPaid(order, new BigDecimal("100")));
    }

    @Test
    void missingCheckoutResponseOrStatusIsNotSuccessfulPayment() {
        assertThrows(InvalidPaymentAmountException.class, () -> validator.validatePaymentCheckoutResult(null));
        assertThrows(InvalidPaymentAmountException.class, () -> validator.validatePaymentCheckoutResult(new PaymentResponse()));
        assertDoesNotThrow(() -> validator.validatePaymentCheckoutResult(PaymentResponse.builder().status("WAITING_PAYMENT").build()));
    }

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
