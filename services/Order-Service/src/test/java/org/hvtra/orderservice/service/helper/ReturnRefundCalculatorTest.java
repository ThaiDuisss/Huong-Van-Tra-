package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import java.util.Arrays;
import java.util.ArrayList;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

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

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-1"})
    void invalidPurchasedOrReturnedQuantityCannotGenerateRefund(String value) {
        Order order = order(BigDecimal.ZERO);
        OrderItem item = orderItem(order, "3", "100", "0");
        order.setItems(List.of(item));
        OrderReturn current = orderReturn(ReturnStatus.RECEIVED, Instant.parse("2026-11-03T00:00:00Z"));
        OrderReturnItem returned = returnItem(item, "1");
        current.addItem(returned);
        BigDecimal invalid = value == null ? null : new BigDecimal(value);
        returned.setQuantity(invalid);
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(order, current, null));
        returned.setQuantity(BigDecimal.ONE);
        item.setQuantity(invalid);
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(order, current, null));
    }

    @ParameterizedTest
    @CsvSource({"earlier,33334", "later,33333", "same-time-low-id,33334", "same-time-high-id,33333",
            "missing-time-low-id,33334", "cancelled,33333", "same-id,33333"})
    void cumulativeRoundingUsesChronologicalReturnThenStableIdAndIgnoresCancelledOrSelf(String priorKind, String expected) {
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Order order = order(BigDecimal.ZERO);
        OrderItem sold = orderItem(order, "3", "100000", "0");
        order.setItems(List.of(sold));
        OrderReturn current = orderReturn(ReturnStatus.RECEIVED, now);
        current.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        current.addItem(returnItem(sold, "1"));
        OrderReturn earlier = orderReturn(priorKind.equals("cancelled") ? ReturnStatus.CANCELLED : ReturnStatus.CREATED,
                priorKind.equals("earlier") || priorKind.equals("cancelled") ? now.minusSeconds(1)
                        : priorKind.equals("later") ? now.plusSeconds(1) : priorKind.equals("missing-time-low-id") ? null : now);
        earlier.setId(UUID.fromString(priorKind.equals("same-time-high-id") ? "33333333-3333-3333-3333-333333333333"
                : priorKind.equals("same-id") ? current.getId().toString() : "11111111-1111-1111-1111-111111111111"));
        earlier.addItem(returnItem(sold, "1"));
        OrderReturn noIdentity = new OrderReturn();
        assertEquals(new BigDecimal(expected), calculator.calculate(order, current,
                Arrays.asList(null, noIdentity, earlier, current)).refundAmount());
    }

    @Test void missingMoneyOrEmptyReturnLinesNeverInventRefundAndInvalidIdentityFails() {
        Order order = order(null);
        OrderItem sold = orderItem(order, "1", "0", "0");
        sold.setLineTotal(null);
        sold.setDiscountAmount(null);
        order.setItems(List.of(sold));
        OrderReturn current = orderReturn(ReturnStatus.RECEIVED, null);
        current.setItems(null);
        assertEquals(BigDecimal.ZERO, calculator.calculate(order, current, null).refundAmount());
        current.setItems(new ArrayList<>());
        current.addItem(returnItem(null, "1"));
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(order, current, null));
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(null, current, null));
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(order, null, null));
        order.setItems(null);
        assertThrows(InvalidOrderReturnException.class, () -> calculator.calculate(order, current, null));
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
