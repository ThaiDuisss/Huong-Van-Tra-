package org.hvtra.orderservice.outbox.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.domain.entity.OrderOutboxEvent;
import org.hvtra.orderservice.outbox.event.OrderEventType;
import org.hvtra.orderservice.outbox.resolver.OrderOutboxTopicResolver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderOutboxPublisher {

    private static final String SOURCE = "order-service";
    private static final String AGGREGATE_TYPE = "ORDER";

    private final OutboxEventService<OrderOutboxEvent> orderOutboxEventService;
    private final OrderOutboxTopicResolver orderOutboxTopicResolver;

    public void publishPaymentCompletedOrchestration(Order order, String paymentEventId) {
        publishPaymentCompletedOrchestration(order, paymentEventId, null);
    }

    public void publishPaymentCompletedOrchestration(Order order, String paymentEventId, String sagaId) {
        publish(order, OrderEventType.ORDER_COMPLETED, paymentEventId, "order-completed", sagaId);
        publish(order, OrderEventType.INVENTORY_RESERVATION_CONFIRM_REQUESTED, paymentEventId, "inventory-confirm", sagaId);

        if (order.getCustomerId() != null) {
            publish(order, OrderEventType.CUSTOMER_PURCHASE_UPDATE_REQUESTED, paymentEventId, "customer-purchase-update", sagaId);
        }

        if (!couponUsageIds(order).isEmpty()) {
            publish(order, OrderEventType.PROMOTION_USAGE_COMMIT_REQUESTED, paymentEventId, "promotion-commit", sagaId);
        }
    }

    public void publishOrderPaymentSucceededNotification(
            Order order,
            PaymentOrderEventPayload paymentEvent,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            String sagaId
    ) {
        publishPaymentNotification(
                order,
                paymentEvent,
                OrderEventType.ORDER_PAYMENT_SUCCEEDED,
                "payment-succeeded",
                oldStatus,
                newStatus,
                "Payment succeeded",
                "Order " + order.getOrderCode() + " payment succeeded.",
                sagaId
        );
    }

    public void publishOrderPaymentFailedNotification(
            Order order,
            PaymentOrderEventPayload paymentEvent,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            String sagaId,
            String reason
    ) {
        publishPaymentNotification(
                order,
                paymentEvent,
                OrderEventType.ORDER_PAYMENT_FAILED,
                "payment-failed",
                oldStatus,
                newStatus,
                "Payment failed",
                firstNonBlank(reason, "Order " + order.getOrderCode() + " payment failed."),
                sagaId
        );
    }

    public void publishOrderCancelledNotification(
            Order order,
            PaymentOrderEventPayload paymentEvent,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            String sagaId,
            String reason
    ) {
        publishPaymentNotification(
                order,
                paymentEvent,
                OrderEventType.ORDER_CANCELLED,
                "order-cancelled",
                oldStatus,
                newStatus,
                "Order cancelled",
                firstNonBlank(reason, "Order " + order.getOrderCode() + " was cancelled."),
                sagaId
        );
    }

    private void publish(Order order, String eventType, String causationId, String action, String sagaId) {
        String orderId = order.getId().toString();
        String businessKey = orderId + ":" + action;
        String topic = orderOutboxTopicResolver.resolveTopic(eventType);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = buildPayload(order, eventId, eventType, causationId, sagaId);

        orderOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE,
                orderId,
                orderId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                sagaId,
                firstNonBlank(sagaId, orderId),
                causationId,
                payload,
                buildHeaders(order, eventId, eventType, businessKey, topic, causationId, sagaId)
        ));
    }

    private void publishPaymentNotification(
            Order order,
            PaymentOrderEventPayload paymentEvent,
            String eventType,
            String action,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            String title,
            String message,
            String sagaId
    ) {
        String orderId = order.getId().toString();
        String causationId = paymentCausationId(paymentEvent);
        String businessKey = orderId + ":" + action + ":" + firstNonBlank(causationId, "unknown");
        String topic = orderOutboxTopicResolver.resolveTopic(eventType);
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = buildPaymentNotificationPayload(
                order,
                paymentEvent,
                eventId,
                eventType,
                oldStatus,
                newStatus,
                title,
                message,
                causationId,
                sagaId
        );

        orderOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                AGGREGATE_TYPE,
                orderId,
                orderId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                sagaId,
                firstNonBlank(sagaId, orderId),
                causationId,
                payload,
                buildHeaders(order, eventId, eventType, businessKey, topic, causationId, sagaId)
        ));
    }

    private Map<String, Object> buildPayload(Order order, String eventId, String eventType, String causationId, String sagaId) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("eventKey", order.getId().toString());
        payload.put("orderId", order.getId().toString());
        payload.put("orderCode", order.getOrderCode());
        payload.put("customerId", customerId(order));
        payload.put("userId", createdBy(order));
        payload.put("paymentStatus", order.getPaymentStatus() == null ? null : order.getPaymentStatus().name());
        payload.put("orderStatus", order.getStatus() == null ? null : order.getStatus().name());
        payload.put("paymentMethod", order.getPaymentMethod());
        payload.put("paymentCode", order.getPaymentCode());
        payload.put("finalAmount", order.getFinalAmount());
        payload.put("paidAmount", order.getPaidAmount());
        payload.put("discountAmount", order.getDiscountAmount());
        payload.put("usageIds", couponUsageIds(order));
        payload.put("sagaId", sagaId);
        payload.put("correlationId", firstNonBlank(sagaId, order.getId().toString()));
        payload.put("causationId", causationId);
        payload.put("occurredAt", Instant.now());

        return payload;
    }

    private Map<String, Object> buildPaymentNotificationPayload(
            Order order,
            PaymentOrderEventPayload paymentEvent,
            String eventId,
            String eventType,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            String title,
            String message,
            String causationId,
            String sagaId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("eventKey", order.getId().toString());
        payload.put("orderId", order.getId().toString());
        payload.put("orderCode", order.getOrderCode());
        payload.put("customerId", customerId(order));
        payload.put("userId", createdBy(order));
        payload.put("paymentId", paymentEvent == null ? null : paymentEvent.getPaymentId());
        payload.put("paymentCode", firstNonBlank(
                paymentEvent == null ? null : paymentEvent.getPaymentCode(),
                order.getPaymentCode()
        ));
        payload.put("oldStatus", oldStatus == null ? null : oldStatus.name());
        payload.put("newStatus", newStatus == null ? null : newStatus.name());
        payload.put("title", title);
        payload.put("message", message);
        payload.put("sagaId", sagaId);
        payload.put("correlationId", firstNonBlank(sagaId, order.getId().toString()));
        payload.put("causationId", causationId);
        payload.put("occurredAt", Instant.now());

        return payload;
    }

    private Map<String, Object> buildHeaders(
            Order order,
            String eventId,
            String eventType,
            String businessKey,
            String topic,
            String causationId,
            String sagaId
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();

        headers.put("source", SOURCE);
        headers.put("sourceService", SOURCE);
        headers.put("aggregateType", AGGREGATE_TYPE);
        headers.put("aggregateId", order.getId().toString());
        headers.put("eventId", eventId);
        headers.put("event_id", eventId);
        headers.put("eventType", eventType);
        headers.put("messageKey", order.getId().toString());
        headers.put("businessKey", businessKey);
        headers.put("topic", topic);
        headers.put("sagaId", sagaId);
        headers.put("saga_id", sagaId);
        headers.put("correlationId", firstNonBlank(sagaId, order.getId().toString()));
        headers.put("correlation_id", firstNonBlank(sagaId, order.getId().toString()));
        headers.put("causationId", causationId);
        headers.put("causation_id", causationId);

        return headers;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String paymentCausationId(PaymentOrderEventPayload event) {
        if (event == null) {
            return null;
        }
        return firstNonBlank(event.getEventId(), event.getEventKey());
    }

    private String customerId(Order order) {
        return order.getCustomerId() == null ? null : order.getCustomerId().toString();
    }

    private String createdBy(Order order) {
        return order.getCreatedBy() == null ? null : order.getCreatedBy().toString();
    }

    private List<String> couponUsageIds(Order order) {
        if (order.getDiscounts() == null) {
            return List.of();
        }

        return order.getDiscounts().stream()
                .map(discount -> discount.getUsageId())
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .distinct()
                .toList();
    }

}
