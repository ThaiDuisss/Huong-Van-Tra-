package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ReturnRefundCalculator {

    private static final int MONEY_SCALE = 0;
    private static final List<ReturnStatus> REFUND_ALLOCATION_STATUSES =
            List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);

    public RefundCalculationResult calculate(
            Order order,
            OrderReturn currentReturn,
            List<OrderReturn> orderReturns
    ) {
        if (order == null || currentReturn == null) {
            throw new InvalidOrderReturnException("Return refund data is invalid");
        }

        Map<UUID, BigDecimal> refundableByOrderItem = refundableAmountByOrderItem(order);
        Map<UUID, BigDecimal> previousReturnedQuantity =
                previousReturnedQuantityByOrderItem(currentReturn, orderReturns);

        BigDecimal refundAmount = BigDecimal.ZERO;
        for (OrderReturnItem returnItem : safeReturnItems(currentReturn.getItems())) {
            OrderItem orderItem = returnItem.getOrderItem();
            if (orderItem == null || orderItem.getId() == null) {
                throw new InvalidOrderReturnException("Return item is invalid");
            }

            BigDecimal itemRefundable = refundableByOrderItem.getOrDefault(orderItem.getId(), BigDecimal.ZERO);
            BigDecimal purchasedQuantity = requirePositive(orderItem.getQuantity(), "Purchased quantity is invalid");
            BigDecimal currentQuantity = requirePositive(returnItem.getQuantity(), "Return quantity must be greater than 0");
            BigDecimal previousQuantity = previousReturnedQuantity.getOrDefault(orderItem.getId(), BigDecimal.ZERO);

            BigDecimal cumulativeBefore = prorate(itemRefundable, previousQuantity, purchasedQuantity);
            BigDecimal cumulativeAfter = prorate(
                    itemRefundable,
                    previousQuantity.add(currentQuantity),
                    purchasedQuantity
            );
            refundAmount = refundAmount.add(cumulativeAfter.subtract(cumulativeBefore));
        }

        return new RefundCalculationResult(refundAmount.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private Map<UUID, BigDecimal> refundableAmountByOrderItem(Order order) {
        List<OrderItem> items = safeOrderItems(order.getItems()).stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .sorted(Comparator.comparing(item -> item.getId() == null ? "" : item.getId().toString()))
                .toList();
        if (items.isEmpty()) {
            throw new InvalidOrderReturnException("Order items are required");
        }

        BigDecimal totalItemDiscount = items.stream()
                .map(item -> defaultZero(item.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderLevelDiscount = defaultZero(order.getDiscountAmount())
                .subtract(totalItemDiscount)
                .max(BigDecimal.ZERO);
        BigDecimal totalLineAmount = items.stream()
                .map(item -> defaultZero(item.getLineTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, BigDecimal> allocationByItem = allocateOrderLevelDiscount(
                items,
                totalLineAmount,
                orderLevelDiscount
        );

        Map<UUID, BigDecimal> refundableByItem = new HashMap<>();
        for (OrderItem item : items) {
            BigDecimal lineTotal = defaultZero(item.getLineTotal());
            BigDecimal allocatedOrderDiscount = allocationByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            refundableByItem.put(item.getId(), lineTotal.subtract(allocatedOrderDiscount).max(BigDecimal.ZERO));
        }
        return refundableByItem;
    }

    private Map<UUID, BigDecimal> allocateOrderLevelDiscount(
            List<OrderItem> items,
            BigDecimal totalLineAmount,
            BigDecimal orderLevelDiscount
    ) {
        Map<UUID, BigDecimal> allocations = new HashMap<>();
        if (orderLevelDiscount.compareTo(BigDecimal.ZERO) <= 0
                || totalLineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            items.forEach(item -> allocations.put(item.getId(), BigDecimal.ZERO.setScale(MONEY_SCALE)));
            return allocations;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            BigDecimal allocation = index == items.size() - 1
                    ? orderLevelDiscount.subtract(allocated)
                    : orderLevelDiscount
                            .multiply(defaultZero(item.getLineTotal()))
                            .divide(totalLineAmount, MONEY_SCALE, RoundingMode.HALF_UP);
            allocation = allocation.max(BigDecimal.ZERO);
            allocations.put(item.getId(), allocation);
            allocated = allocated.add(allocation);
        }
        return allocations;
    }

    private Map<UUID, BigDecimal> previousReturnedQuantityByOrderItem(
            OrderReturn currentReturn,
            List<OrderReturn> orderReturns
    ) {
        Map<UUID, BigDecimal> quantities = new HashMap<>();
        for (OrderReturn orderReturn : safeReturns(orderReturns).stream()
                .filter(orderReturn -> isBeforeCurrentReturn(orderReturn, currentReturn))
                .filter(orderReturn -> REFUND_ALLOCATION_STATUSES.contains(orderReturn.getStatus()))
                .toList()) {
            for (OrderReturnItem item : safeReturnItems(orderReturn.getItems())) {
                if (item.getOrderItem() == null || item.getOrderItem().getId() == null) {
                    continue;
                }
                quantities.merge(
                        item.getOrderItem().getId(),
                        defaultZero(item.getQuantity()),
                        BigDecimal::add
                );
            }
        }
        return quantities;
    }

    private boolean isBeforeCurrentReturn(OrderReturn candidate, OrderReturn currentReturn) {
        if (candidate == null || candidate.getId() == null || candidate.getId().equals(currentReturn.getId())) {
            return false;
        }

        Instant candidateCreatedAt = candidate.getCreatedAt();
        Instant currentCreatedAt = currentReturn.getCreatedAt();
        if (candidateCreatedAt != null && currentCreatedAt != null) {
            int createdCompare = candidateCreatedAt.compareTo(currentCreatedAt);
            if (createdCompare != 0) {
                return createdCompare < 0;
            }
        }

        return candidate.getId().toString().compareTo(currentReturn.getId().toString()) < 0;
    }

    private BigDecimal prorate(
            BigDecimal itemRefundable,
            BigDecimal quantity,
            BigDecimal purchasedQuantity
    ) {
        BigDecimal cappedQuantity = quantity.min(purchasedQuantity).max(BigDecimal.ZERO);
        return itemRefundable
                .multiply(cappedQuantity)
                .divide(purchasedQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        BigDecimal normalized = defaultZero(value);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderReturnException(message);
        }
        return normalized;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<OrderItem> safeOrderItems(List<OrderItem> items) {
        return items == null ? List.of() : items;
    }

    private List<OrderReturnItem> safeReturnItems(Collection<OrderReturnItem> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private List<OrderReturn> safeReturns(List<OrderReturn> returns) {
        return returns == null ? List.of() : returns;
    }

    public record RefundCalculationResult(BigDecimal refundAmount) {}
}
