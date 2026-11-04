package org.hvtra.orderservice.outbox;

import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderOutboxTopicResolverTest {
    private final OrderOutboxTopicResolver resolver = new OrderOutboxTopicResolver();

    @ParameterizedTest
    @CsvSource({"ORDER_COMPLETED,order.completed", "ORDER_PAYMENT_SUCCEEDED,notification.order.payment-succeeded",
            "ORDER_PAYMENT_FAILED,notification.order.payment-failed", "ORDER_PAYMENT_NEED_REVIEW,notification.order.payment-need-review",
            "ORDER_CANCELLED,notification.order.cancelled", "RETURN_CREATED,notification.return.created",
            "RETURN_RECEIVED,notification.return.received", "RETURN_CANCELLED,notification.return.cancelled",
            "RETURN_REFUND_COMPLETED,notification.return.refund-completed", "RETURN_COMPLETED,notification.return.completed",
            "RETURN_INVENTORY_DISPOSITION_REQUESTED,order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED",
            "INVENTORY_RESERVATION_CONFIRM_REQUESTED,inventory.reservation-confirm.requested",
            "CUSTOMER_PURCHASE_UPDATE_REQUESTED,customer.purchase-update.requested",
            "PROMOTION_USAGE_COMMIT_REQUESTED,promotion.usage-commit.requested"})
    void routesEachBusinessEventToItsOwnedTopic(String eventType, String topic) {
        assertEquals(topic, resolver.resolveTopic(eventType));
    }

    @Test
    void rejectsUnknownEventInsteadOfPublishingToUnrelatedTopic() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveTopic("UNSUPPORTED_EVENT"));
    }
}
