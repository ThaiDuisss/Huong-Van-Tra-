package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.DiscountType;
import org.hvtra.orderservice.dto.request.CreateDiscountRequest;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderAmountCalculatorTest {

    private final OrderAmountCalculator calculator = new OrderAmountCalculator();

    @Test
    void discountsApplySequentiallyToRemainingAmountAndRoundPerStep() {
        BigDecimal discount = calculator.calculateDiscounts(new BigDecimal("100.05"), List.of(
                discount(DiscountType.PERCENTAGE, "10"), discount(DiscountType.PERCENTAGE, "50")));
        // 10.005 -> 10.01, remaining 90.04 -> second discount 45.02.
        assertEquals(new BigDecimal("55.03"), discount);
    }

    @ParameterizedTest
    @CsvSource({"PERCENTAGE, 100, 100.00", "FIXED_AMOUNT, 100, 100.00", "FIXED_AMOUNT, 1.005, 1.01"})
    void acceptsExactDiscountBoundaryAndRoundsFixedMoney(DiscountType type, String value, String expected) {
        assertEquals(new BigDecimal(expected), calculator.calculateDiscountAmount(
                new BigDecimal("100.00"), discount(type, value)));
    }

    @ParameterizedTest
    @CsvSource({"PERCENTAGE, 0", "PERCENTAGE, -1", "PERCENTAGE, 100.01", "FIXED_AMOUNT, 100.01"})
    void rejectsInvalidDiscountWithoutProducingNegativeRemaining(DiscountType type, String value) {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscounts(
                new BigDecimal("100"), List.of(discount(type, value))));
    }

    @Test
    void rejectsIncompleteDiscountMetadataAndInvalidBase() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscountAmount(BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscountAmount(
                BigDecimal.TEN, new CreateDiscountRequest()));
        CreateDiscountRequest missingValue = new CreateDiscountRequest();
        missingValue.setDiscountType(DiscountType.FIXED_AMOUNT);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscountAmount(BigDecimal.TEN, missingValue));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscounts(
                null, List.of(discount(DiscountType.FIXED_AMOUNT, "1"))));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscounts(
                BigDecimal.ONE.negate(), List.of(discount(DiscountType.FIXED_AMOUNT, "1"))));
        assertEquals(BigDecimal.ZERO, calculator.calculateDiscounts(BigDecimal.TEN, null));
        assertEquals(BigDecimal.ZERO, calculator.calculateDiscounts(BigDecimal.TEN, List.of()));
    }

    @Test
    void cumulativeFixedDiscountCannotExceedRemainingAfterPercentage() {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateDiscounts(BigDecimal.TEN,
                List.of(discount(DiscountType.PERCENTAGE, "50"), discount(DiscountType.FIXED_AMOUNT, "5.01"))));
    }

    @Test
    void recalculatingPaidOrderKeepsPaymentLedgerSnapshot() {
        Order order = Order.builder().items(List.of(OrderItem.builder().quantity(new BigDecimal("3"))
                        .actualUnitPrice(new BigDecimal("10.25")).build()))
                .paymentStatus(PaymentStatus.PAID).paidAmount(new BigDecimal("30.75"))
                .debtAmount(BigDecimal.ZERO).build();
        calculator.recalculateOrderTotals(order);
        assertEquals(new BigDecimal("30.75"), order.getFinalAmount());
        assertEquals(new BigDecimal("30.75"), order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());
    }

    @Test
    void creationTotalsInitializeDebtAndClampNegativeFinalAmount() {
        Order order = new Order();
        calculator.applyCreatedOrderTotals(order, new BigDecimal("10"), new BigDecimal("15"), BigDecimal.ONE);
        assertEquals(BigDecimal.ZERO, order.getFinalAmount());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());
        assertEquals(new BigDecimal("10"), order.getOriginalAmount());
        assertEquals(new BigDecimal("15"), order.getDiscountAmount());
        calculator.applyCreatedOrderTotals(order, null, null, null);
        assertEquals(BigDecimal.ZERO, order.getFinalAmount());
    }

    @Test
    void emptySummaryAndNullableQuantitiesDoNotInventRevenue() {
        TodaySalesSummaryResponse summary = calculator.calculateTodaySalesSummary(List.of());
        assertEquals(0, summary.getTotalOrders());
        assertEquals(BigDecimal.ZERO, summary.getAverageOrderValue());
        assertEquals(BigDecimal.ZERO, summary.getPendingAmount());
        Order order = new Order();
        order.setItems(null);
        assertEquals(BigDecimal.ZERO, calculator.totalItems(order));
        order.setItems(List.of(new OrderItem(), OrderItem.builder().quantity(new BigDecimal("2.5")).build()));
        assertEquals(new BigDecimal("2.5"), calculator.totalItems(order));
    }

    private CreateDiscountRequest discount(DiscountType type, String value) {
        CreateDiscountRequest request = new CreateDiscountRequest();
        request.setDiscountType(type);
        request.setDiscountValue(new BigDecimal(value));
        return request;
    }

    @Test
    void creationLineTotalKeepsExistingUnclampedFormula() {
        BigDecimal result = calculator.calculateCreationLineTotal(
                new BigDecimal("10.00"),
                new BigDecimal("2"),
                new BigDecimal("25.00")
        );

        assertEquals(new BigDecimal("-5.00"), result);
    }

    @Test
    void recalculationClampsLineAndFinalAmountsAtZero() {
        OrderItem item = OrderItem.builder()
                .quantity(new BigDecimal("2"))
                .actualUnitPrice(new BigDecimal("10.00"))
                .discountAmount(new BigDecimal("25.00"))
                .build();
        Order order = Order.builder()
                .items(List.of(item))
                .discountAmount(new BigDecimal("3.00"))
                .shippingFee(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .paidAmount(new BigDecimal("99.00"))
                .debtAmount(BigDecimal.ZERO)
                .build();

        calculator.recalculateOrderTotals(order);

        assertEquals(BigDecimal.ZERO, item.getLineTotal());
        assertEquals(BigDecimal.ZERO, order.getOriginalAmount());
        assertEquals(BigDecimal.ZERO, order.getFinalAmount());
        assertEquals(BigDecimal.ZERO, order.getPaidAmount());
        assertEquals(BigDecimal.ZERO, order.getDebtAmount());
    }

    @Test
    void todaySummaryKeepsExistingClassificationAndRounding() {
        Order firstCompleted = order(
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                new BigDecimal("10.00"),
                BigDecimal.ZERO
        );
        Order secondCompleted = order(
                OrderStatus.COMPLETED,
                PaymentStatus.PAID,
                new BigDecimal("11.00"),
                BigDecimal.ZERO
        );
        Order pending = order(
                OrderStatus.PENDING_PAYMENT,
                PaymentStatus.WAITING_PAYMENT,
                new BigDecimal("7.00"),
                new BigDecimal("5.00")
        );
        Order cancelled = order(
                OrderStatus.CANCELLED,
                PaymentStatus.WAITING_PAYMENT,
                new BigDecimal("100.00"),
                new BigDecimal("100.00")
        );

        TodaySalesSummaryResponse summary = calculator.calculateTodaySalesSummary(
                List.of(firstCompleted, secondCompleted, pending, cancelled)
        );

        assertEquals(4, summary.getTotalOrders());
        assertEquals(2, summary.getCompletedOrders());
        assertEquals(1, summary.getPendingPaymentOrders());
        assertEquals(new BigDecimal("21.00"), summary.getTodayRevenue());
        assertEquals(new BigDecimal("10.50"), summary.getAverageOrderValue());
        assertEquals(new BigDecimal("5.00"), summary.getPendingAmount());
    }

    private Order order(
            OrderStatus status,
            PaymentStatus paymentStatus,
            BigDecimal finalAmount,
            BigDecimal debtAmount
    ) {
        return Order.builder()
                .status(status)
                .paymentStatus(paymentStatus)
                .finalAmount(finalAmount)
                .debtAmount(debtAmount)
                .build();
    }
}
