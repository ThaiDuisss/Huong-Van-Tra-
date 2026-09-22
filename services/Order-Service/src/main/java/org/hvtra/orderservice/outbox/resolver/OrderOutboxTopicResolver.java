package org.hvtra.orderservice.outbox.resolver;

import org.hvtra.commonservice.common_core.kafka.outbox.resolver.OutboxTopicResolver;
import org.hvtra.orderservice.outbox.event.OrderEventType;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxTopicResolver implements OutboxTopicResolver {

    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case OrderEventType.ORDER_COMPLETED -> "order.completed";
            case OrderEventType.ORDER_PAYMENT_SUCCEEDED -> "notification.order.payment-succeeded";
            case OrderEventType.ORDER_PAYMENT_FAILED -> "notification.order.payment-failed";
            case OrderEventType.ORDER_CANCELLED -> "notification.order.cancelled";
            case OrderEventType.RETURN_CREATED -> "notification.return.created";
            case OrderEventType.RETURN_RECEIVED -> "notification.return.received";
            case OrderEventType.RETURN_CANCELLED -> "notification.return.cancelled";
            case OrderEventType.RETURN_REFUND_COMPLETED -> "notification.return.refund-completed";
            case OrderEventType.RETURN_COMPLETED -> "notification.return.completed";
            case OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED ->
                    "order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED";
            case OrderEventType.INVENTORY_RESERVATION_CONFIRM_REQUESTED -> "inventory.reservation-confirm.requested";
            case OrderEventType.CUSTOMER_PURCHASE_UPDATE_REQUESTED -> "customer.purchase-update.requested";
            case OrderEventType.PROMOTION_USAGE_COMMIT_REQUESTED -> "promotion.usage-commit.requested";
            default -> throw new IllegalArgumentException("Unknown order event type: " + eventType);
        };
    }
}
