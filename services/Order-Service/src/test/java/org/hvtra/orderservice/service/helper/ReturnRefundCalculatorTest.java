package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnRefundCalculatorTest {

    private final ReturnRefundCalculator calculator = new ReturnRefundCalculator();

    @Test
    void calculateAllocatesOrderLevelDiscountByLineTotalAndDoesNotRefundShipping() {
        Order order = order(new BigDecimal("10000"));
        OrderItem firstItem = orderItem(order, "2.000", "60000", "0");
        OrderItem secondItem = orderItem(order, "1.000", "40000", "0");
        order.setItems(List.of(firstItem, secondItem));
        OrderReturn currentReturn = orderReturn(ReturnStatus.RECEIVED, Instant.parse("2026-09-10T10:00:00Z"));
        currentReturn.addItem(returnItem(firstItem, "1.000"));

        ReturnRefundCalculator.RefundCalculationResult result =
                calculator.calculate(order, currentReturn, List.of(currentReturn));

        assertEquals(new BigDecimal("27000"), result.refundAmount());
    }

    @Test
    void calculateUsesCumulativeRoundingSoPartialReturnsSumToItemRefundableAmount() {
        Order order = order(BigDecimal.ZERO);
        OrderItem orderItem = orderItem(order, "3.000", "100000", "0");
        order.setItems(List.of(orderItem));
        OrderReturn firstReturn = orderReturn(ReturnStatus.RECEIVED, Instant.parse("2026-09-10T10:00:00Z"));
        firstReturn.addItem(returnItem(orderItem, "1.000"));
        OrderReturn secondReturn = orderReturn(ReturnStatus.RECEIVED, Instant.parse("2026-09-11T10:00:00Z"));
        secondReturn.addItem(returnItem(orderItem, "1.000"));
        OrderReturn thirdReturn = orderReturn(ReturnStatus.RECEIVED, Instant.parse("2026-09-12T10:00:00Z"));
        thirdReturn.addItem(returnItem(orderItem, "1.000"));

        BigDecimal firstRefund = calculator
                .calculate(order, firstReturn, List.of(firstReturn, secondReturn, thirdReturn))
                .refundAmount();
        BigDecimal secondRefund = calculator
                .calculate(order, secondReturn, List.of(firstReturn, secondReturn, thirdReturn))
                .refundAmount();
        BigDecimal thirdRefund = calculator
                .calculate(order, thirdReturn, List.of(firstReturn, secondReturn, thirdReturn))
                .refundAmount();

        assertEquals(new BigDecimal("33333"), firstRefund);
        assertEquals(new BigDecimal("33334"), secondRefund);
        assertEquals(new BigDecimal("33333"), thirdRefund);
        assertEquals(new BigDecimal("100000"), firstRefund.add(secondRefund).add(thirdRefund));
    }

    private Order order(BigDecimal discountAmount) {
        Order value = Order.builder()
                .orderCode("ORD-001")
                .discountAmount(discountAmount)
                .shippingFee(new BigDecimal("25000"))
                .build();
        value.setId(UUID.randomUUID());
        value.setIsDeleted(false);
        return value;
    }

    private OrderItem orderItem(
            Order order,
            String quantity,
            String lineTotal,
            String discountAmount
    ) {
        OrderItem value = OrderItem.builder()
                .order(order)
                .productVariantId(UUID.randomUUID())
                .productNameSnapshot("Tra sen")
                .quantity(new BigDecimal(quantity))
                .actualUnitPrice(BigDecimal.ZERO)
                .discountAmount(new BigDecimal(discountAmount))
                .lineTotal(new BigDecimal(lineTotal))
                .build();
        value.setId(UUID.randomUUID());
        value.setIsDeleted(false);
        return value;
    }

    private OrderReturn orderReturn(ReturnStatus status, Instant createdAt) {
        OrderReturn value = OrderReturn.builder()
                .returnCode("RET-" + UUID.randomUUID())
                .status(status)
                .build();
        value.setId(UUID.randomUUID());
        value.setCreatedAt(createdAt);
        value.setIsDeleted(false);
        return value;
    }

    private OrderReturnItem returnItem(OrderItem orderItem, String quantity) {
        return OrderReturnItem.builder()
                .orderItem(orderItem)
                .quantity(new BigDecimal(quantity))
                .reason(ReturnReason.QUALITY_ISSUE)
                .condition(ReturnCondition.OPENED)
                .build();
    }
}
