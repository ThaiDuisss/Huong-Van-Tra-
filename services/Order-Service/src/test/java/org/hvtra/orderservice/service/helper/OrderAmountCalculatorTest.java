package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderAmountCalculatorTest {

    private final OrderAmountCalculator calculator = new OrderAmountCalculator();

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
