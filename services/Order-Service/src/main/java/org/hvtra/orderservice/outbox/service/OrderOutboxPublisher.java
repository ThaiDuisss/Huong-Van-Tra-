package org.hvtra.orderservice.outbox.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.kafka.outbox.service.OutboxEventService;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommandLine;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.client.PaymentClient;
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
    private static final String RETURN_AGGREGATE_TYPE = "ORDER_RETURN";
    private static final String RETURN_INVENTORY_COMMAND_AGGREGATE_TYPE =
            "ORDER_RETURN_INVENTORY_COMMAND";

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

    public void publishReturnCreatedNotification(OrderReturn orderReturn, UUID actorUserId) {
        publishReturnNotification(
                orderReturn,
                OrderEventType.RETURN_CREATED,
                "return-created",
                null,
                ReturnStatus.CREATED,
                actorUserId,
                "Phiếu hoàn hàng đã được tạo",
                "Phiếu hoàn hàng " + orderReturn.getReturnCode() + " đã được tạo.",
                null,
                null
        );
    }

    public void publishReturnReceivedNotification(
            OrderReturn orderReturn,
            ReturnStatus oldStatus,
            UUID actorUserId
    ) {
        publishReturnNotification(
                orderReturn,
                OrderEventType.RETURN_RECEIVED,
                "return-received",
                oldStatus,
                ReturnStatus.RECEIVED,
                actorUserId,
                "Đã nhận hàng trả",
                "Cửa hàng đã nhận hàng trả của phiếu " + orderReturn.getReturnCode() + ".",
                null,
                null
        );
    }

    public void publishReturnCancelledNotification(
            OrderReturn orderReturn,
            ReturnStatus oldStatus,
            UUID actorUserId
    ) {
        publishReturnNotification(
                orderReturn,
                OrderEventType.RETURN_CANCELLED,
                "return-cancelled",
                oldStatus,
                ReturnStatus.CANCELLED,
                actorUserId,
                "Phiếu hoàn hàng đã bị hủy",
                "Phiếu hoàn hàng " + orderReturn.getReturnCode() + " đã bị hủy.",
                null,
                null
        );
    }

    public void publishReturnRefundCompletedNotification(
            OrderReturn orderReturn,
            ReturnStatus oldStatus,
            UUID actorUserId,
            PaymentClient.PaymentRefundResponse paymentRefund
    ) {
        publishReturnNotification(
                orderReturn,
                OrderEventType.RETURN_REFUND_COMPLETED,
                "return-refund-completed",
                oldStatus,
                ReturnStatus.COMPLETED,
                actorUserId,
                "Hoàn tiền thành công",
                "Phiếu " + orderReturn.getReturnCode() + " đã hoàn tiền thành công.",
                paymentRefund == null ? null : paymentRefund.refundTransactionId(),
                paymentRefund == null ? null : paymentRefund.paymentId()
        );
    }

    public void publishReturnCompletedNotification(
            OrderReturn orderReturn,
            ReturnStatus oldStatus,
            UUID actorUserId
    ) {
        publishReturnNotification(
                orderReturn,
                OrderEventType.RETURN_COMPLETED,
                "return-completed",
                oldStatus,
                ReturnStatus.COMPLETED,
                actorUserId,
                "Xử lý hàng trả hoàn tất",
                "Phiếu hoàn " + orderReturn.getReturnCode() + " đã hoàn tất xử lý hàng. Không phát sinh hoàn tiền.",
                null,
                null
        );
    }

    public void publishReturnInventoryDispositionRequested(
            OrderReturnInventoryCommand command,
            UUID actorUserId
    ) {
        OrderReturn orderReturn = command.getOrderReturn();
        Order order = orderReturn.getOrder();
        String commandId = command.getCommandId().toString();
        String topic = orderOutboxTopicResolver.resolveTopic(
                OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED
        );
        String eventId = UUID.randomUUID().toString();
        String actor = actorUserId == null ? null : actorUserId.toString();
        Map<String, Object> payload = buildReturnInventoryDispositionPayload(
                command,
                eventId,
                actor
        );

        orderOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                RETURN_INVENTORY_COMMAND_AGGREGATE_TYPE,
                commandId,
                commandId,
                OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED,
                commandId,
                topic,
                SOURCE,
                null,
                orderReturn.getId().toString(),
                actor,
                payload,
                buildHeaders(
                        order,
                        eventId,
                        OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED,
                        commandId,
                        topic,
                        actor,
                        null,
                        RETURN_INVENTORY_COMMAND_AGGREGATE_TYPE,
                        commandId
                )
        ));
    }

    public void publishReturnInventoryDispositionRecovery(
            OrderReturnInventoryCommand command,
            int recoveryAttempt
    ) {
        OrderReturn orderReturn = command.getOrderReturn();
        Order order = orderReturn.getOrder();
        String commandId = command.getCommandId().toString();
        String topic = orderOutboxTopicResolver.resolveTopic(
                OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED
        );
        String eventId = UUID.randomUUID().toString();
        String actor = command.getRequestedBy() == null ? null : command.getRequestedBy().toString();
        Map<String, Object> payload = buildReturnInventoryDispositionPayload(
                command,
                eventId,
                actor
        );
        String businessKey = commandId + ":recovery:" + recoveryAttempt;

        orderOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                RETURN_INVENTORY_COMMAND_AGGREGATE_TYPE,
                commandId,
                commandId,
                OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED,
                businessKey,
                topic,
                SOURCE,
                null,
                orderReturn.getId().toString(),
                actor,
                payload,
                buildHeaders(
                        order,
                        eventId,
                        OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED,
                        commandId,
                        topic,
                        actor,
                        null,
                        RETURN_INVENTORY_COMMAND_AGGREGATE_TYPE,
                        commandId
                )
        ));
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

    private void publishReturnNotification(
            OrderReturn orderReturn,
            String eventType,
            String action,
            ReturnStatus oldStatus,
            ReturnStatus newStatus,
            UUID actorUserId,
            String title,
            String message,
            UUID refundTransactionId,
            UUID paymentId
    ) {
        Order order = orderReturn.getOrder();
        String returnId = orderReturn.getId().toString();
        String businessKey = returnId + ":" + action;
        String topic = orderOutboxTopicResolver.resolveTopic(eventType);
        String eventId = UUID.randomUUID().toString();
        String actor = actorUserId == null ? null : actorUserId.toString();
        Map<String, Object> payload = buildReturnNotificationPayload(
                orderReturn,
                eventId,
                eventType,
                oldStatus,
                newStatus,
                actor,
                title,
                message,
                refundTransactionId,
                paymentId
        );

        orderOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                eventId,
                RETURN_AGGREGATE_TYPE,
                returnId,
                returnId,
                eventType,
                businessKey,
                topic,
                SOURCE,
                null,
                returnId,
                actor,
                payload,
                buildHeaders(
                        order,
                        eventId,
                        eventType,
                        businessKey,
                        topic,
                        actor,
                        null,
                        RETURN_AGGREGATE_TYPE,
                        returnId
                )
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

    private Map<String, Object> buildReturnNotificationPayload(
            OrderReturn orderReturn,
            String eventId,
            String eventType,
            ReturnStatus oldStatus,
            ReturnStatus newStatus,
            String actorUserId,
            String title,
            String message,
            UUID refundTransactionId,
            UUID paymentId
    ) {
        Order order = orderReturn.getOrder();
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("eventId", eventId);
        payload.put("eventType", eventType);
        payload.put("eventKey", orderReturn.getId().toString());
        payload.put("returnId", orderReturn.getId().toString());
        payload.put("returnCode", orderReturn.getReturnCode());
        payload.put("orderId", order.getId().toString());
        payload.put("orderCode", order.getOrderCode());
        payload.put("branchId", order.getBranchId() == null ? null : order.getBranchId().toString());
        payload.put("customerId", customerId(order));
        payload.put("userId", firstNonBlank(createdBy(order), actorUserId));
        payload.put("actorUserId", actorUserId);
        payload.put("paymentId", paymentId == null ? null : paymentId.toString());
        payload.put("refundTransactionId", refundTransactionId == null ? null : refundTransactionId.toString());
        payload.put("oldStatus", oldStatus == null ? null : oldStatus.name());
        payload.put("newStatus", newStatus == null ? null : newStatus.name());
        payload.put("title", title);
        payload.put("message", message);
        payload.put("sagaId", null);
        payload.put("correlationId", orderReturn.getId().toString());
        payload.put("causationId", actorUserId);
        payload.put("occurredAt", Instant.now());

        return payload;
    }

    private Map<String, Object> buildReturnInventoryDispositionPayload(
            OrderReturnInventoryCommand command,
            String eventId,
            String actorUserId
    ) {
        OrderReturn orderReturn = command.getOrderReturn();
        Order order = orderReturn.getOrder();
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("eventId", eventId);
        payload.put("eventType", OrderEventType.RETURN_INVENTORY_DISPOSITION_REQUESTED);
        payload.put("eventKey", command.getCommandId().toString());
        payload.put("commandId", command.getCommandId().toString());
        payload.put("returnId", orderReturn.getId().toString());
        payload.put("returnCode", orderReturn.getReturnCode());
        payload.put("orderId", order.getId().toString());
        payload.put("orderCode", order.getOrderCode());
        payload.put("branchId", order.getBranchId() == null ? null : order.getBranchId().toString());
        payload.put("status", command.getStatus() == null ? null : command.getStatus().name());
        payload.put("requestedBy", actorUserId);
        payload.put("lines", commandLines(command));
        payload.put("sagaId", null);
        payload.put("correlationId", orderReturn.getId().toString());
        payload.put("causationId", actorUserId);
        payload.put("occurredAt", Instant.now());

        return payload;
    }

    private List<Map<String, Object>> commandLines(OrderReturnInventoryCommand command) {
        if (command.getLines() == null) {
            return List.of();
        }

        return command.getLines().stream()
                .filter(line -> Boolean.FALSE.equals(line.getIsDeleted()))
                .map(this::commandLine)
                .toList();
    }

    private Map<String, Object> commandLine(OrderReturnInventoryCommandLine line) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("returnItemId", line.getReturnItem().getId().toString());
        payload.put("orderItemId", line.getOrderItemId().toString());
        payload.put("productVariantId", line.getProductVariantId().toString());
        payload.put("quantity", line.getQuantity());
        payload.put("result", line.getResult() == null ? null : line.getResult().name());
        payload.put("destinationLocationId", line.getDestinationLocationId() == null
                ? null
                : line.getDestinationLocationId().toString());
        payload.put("note", line.getNote());

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
        return buildHeaders(
                order,
                eventId,
                eventType,
                businessKey,
                topic,
                causationId,
                sagaId,
                AGGREGATE_TYPE,
                order.getId().toString()
        );
    }

    private Map<String, Object> buildHeaders(
            Order order,
            String eventId,
            String eventType,
            String businessKey,
            String topic,
            String causationId,
            String sagaId,
            String aggregateType,
            String aggregateId
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();

        headers.put("source", SOURCE);
        headers.put("sourceService", SOURCE);
        headers.put("aggregateType", aggregateType);
        headers.put("aggregateId", aggregateId);
        headers.put("eventId", eventId);
        headers.put("event_id", eventId);
        headers.put("eventType", eventType);
        headers.put("messageKey", aggregateId);
        headers.put("businessKey", businessKey);
        headers.put("topic", topic);
        headers.put("sagaId", sagaId);
        headers.put("saga_id", sagaId);
        headers.put("correlationId", firstNonBlank(sagaId, aggregateId));
        headers.put("correlation_id", firstNonBlank(sagaId, aggregateId));
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
