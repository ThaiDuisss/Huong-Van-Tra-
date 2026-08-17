package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class OrderAmountCalculator {

    public BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public BigDecimal calculateCreationLineTotal(
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal discountAmount
    ) {
        return defaultZero(unitPrice)
                .multiply(defaultZero(quantity))
                .subtract(defaultZero(discountAmount));
    }

    public void applyCreatedOrderTotals(
            Order order,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal shippingFee
    ) {
        BigDecimal finalAmount = defaultZero(originalAmount)
                .subtract(defaultZero(discountAmount))
                .add(defaultZero(shippingFee));
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(finalAmount);
    }

    public void recalculateLine(OrderItem item) {
        BigDecimal quantity = defaultZero(item.getQuantity());
        BigDecimal unitPrice = defaultZero(item.getActualUnitPrice());
        BigDecimal discountAmount = defaultZero(item.getDiscountAmount());
        BigDecimal lineTotal = unitPrice.multiply(quantity).subtract(discountAmount);
        item.setLineTotal(lineTotal.max(BigDecimal.ZERO));
    }

    public void recalculateOrderTotals(Order order) {
        BigDecimal originalAmount = order.getItems() == null
                ? BigDecimal.ZERO
                : order.getItems().stream()
                .peek(this::recalculateLine)
                .map(item -> defaultZero(item.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = defaultZero(order.getDiscountAmount());
        BigDecimal shippingFee = defaultZero(order.getShippingFee());
        BigDecimal finalAmount = originalAmount.subtract(discountAmount).add(shippingFee).max(BigDecimal.ZERO);

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        if (PaymentStatus.PAID != order.getPaymentStatus()) {
            order.setPaidAmount(BigDecimal.ZERO);
            order.setDebtAmount(finalAmount);
        }
    }

    public TodaySalesSummaryResponse calculateTodaySalesSummary(List<Order> todayOrders) {
        long totalOrders = todayOrders.size();
        long completedOrders = todayOrders.stream().filter(this::isCompletedSale).count();
        long pendingPaymentOrders = todayOrders.stream().filter(this::isPendingPaymentSale).count();

        BigDecimal todayRevenue = todayOrders.stream()
                .filter(this::isCompletedSale)
                .map(order -> defaultZero(order.getFinalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = todayOrders.stream()
                .filter(this::isPendingPaymentSale)
                .map(order -> defaultZero(order.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0
                        ? defaultZero(order.getDebtAmount())
                        : defaultZero(order.getFinalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageOrderValue = completedOrders == 0
                ? BigDecimal.ZERO
                : todayRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP);

        return TodaySalesSummaryResponse.builder()
                .todayRevenue(todayRevenue)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .pendingPaymentOrders(pendingPaymentOrders)
                .averageOrderValue(averageOrderValue)
                .pendingAmount(pendingAmount)
                .build();
    }

    public boolean isPendingPaymentSale(Order order) {
        return OrderStatus.CANCELLED != order.getStatus()
                && (PaymentStatus.UNPAID == order.getPaymentStatus()
                || PaymentStatus.WAITING_PAYMENT == order.getPaymentStatus()
                || OrderStatus.PENDING_PAYMENT == order.getStatus());
    }

    public BigDecimal totalItems(Order order) {
        if (order.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(item -> defaultZero(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isCompletedSale(Order order) {
        return OrderStatus.CANCELLED != order.getStatus()
                && (PaymentStatus.PAID == order.getPaymentStatus() || OrderStatus.COMPLETED == order.getStatus());
    }
}
