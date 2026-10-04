package org.hvtra.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.notificationservice.domain.entity.Notification;
import org.hvtra.notificationservice.domain.entity.NotificationInbox;
import org.hvtra.notificationservice.dto.event.OrderNotificationEventPayload;
import org.hvtra.notificationservice.dto.response.NotificationResponse;
import org.hvtra.notificationservice.integration.consumer.OrderNotificationEventMetadataResolver;
import org.hvtra.notificationservice.integration.consumer.OrderNotificationEventPayloadParser;
import org.hvtra.notificationservice.invoice.InvoiceEmailService;
import org.hvtra.notificationservice.repository.NotificationInboxRepository;
import org.hvtra.notificationservice.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationRepository notificationRepository;
    private final NotificationInboxRepository notificationInboxRepository;
    private final OrderNotificationEventPayloadParser payloadParser;
    private final OrderNotificationEventMetadataResolver metadataResolver;
    private final NotificationSseService notificationSseService;
    private final InvoiceEmailService invoiceEmailService;

    @Value("${kafka-consumer-config.group-id:Notification-Service-order-events}")
    private String consumerGroup;

    @Transactional
    public void handleOrderEvent(ConsumerRecord<String, String> record) {
        OrderNotificationEventPayload event = parse(record);
        if (event == null) {
            return;
        }

        metadataResolver.normalizeBeforeEventId(record, event);
        metadataResolver.normalizeEventKey(record, event);
        String eventId = metadataResolver.resolveEventId(record, event);

        NotificationInbox inbox = insertInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            return;
        }

        try {
            inbox.setStatus(InboxStatus.PROCESSING);

            if (!isNotificationEvent(event.getEventType())) {
                markIgnored(inbox);
                return;
            }

            Notification notification = createNotificationIfAbsent(record, event, eventId);
            sendAfterCommit(notification);
            invoiceEmailService.sendAfterCommit(event);
            markProcessed(inbox);
        } catch (RuntimeException ex) {
            log.error(
                    "Không xử lý được order notification event. eventId={}, eventType={}, topic={}, key={}, error={}",
                    eventId,
                    event.getEventType(),
                    record.topic(),
                    record.key(),
                    ex.getMessage(),
                    ex
            );
            markFailed(inbox, ex);
        }
    }

    private OrderNotificationEventPayload parse(ConsumerRecord<String, String> record) {
        try {
            Optional<OrderNotificationEventPayload> parsed = payloadParser.parseCreateEvent(record.value());
            if (parsed.isEmpty()) {
                log.debug("Bỏ qua order notification CDC event không phải create. topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
                return null;
            }
            return parsed.get();
        } catch (RuntimeException ex) {
            log.error(
                    "Không parse được order notification event. eventId={}, eventType={}, topic={}, key={}, error={}",
                    header(record, "event_id"),
                    header(record, "event_type"),
                    record.topic(),
                    record.key(),
                    ex.getMessage(),
                    ex
            );
            throw ex;
        }
    }

    private NotificationInbox insertInbox(
            ConsumerRecord<String, String> record,
            OrderNotificationEventPayload event,
            String eventId
    ) {
        Optional<NotificationInbox> existing = notificationInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            NotificationInbox inbox = existing.get();
            if (isTerminal(inbox.getStatus())) {
                log.debug("Duplicate notification event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
                return inbox;
            }

            throw new IllegalStateException(
                    "Duplicate notification event is not completed yet. eventId=" + eventId + ", status=" + inbox.getStatus()
            );
        }

        try {
            NotificationInbox inbox = buildInbox(record, event, eventId);
            return notificationInboxRepository.saveAndFlush(inbox);
        } catch (DataIntegrityViolationException ex) {
            return notificationInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)
                    .orElseThrow(() -> ex);
        }
    }

    private NotificationInbox buildInbox(
            ConsumerRecord<String, String> record,
            OrderNotificationEventPayload event,
            String eventId
    ) {
        NotificationInbox inbox = new NotificationInbox();

        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record));
        inbox.setEntityType(metadataResolver.aggregateType(record));
        inbox.setEntityId(metadataResolver.aggregateId(record, event));
        inbox.setSagaId(event.getSagaId());
        inbox.setCorrelationId(metadataResolver.correlationId(record, event));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);

        return inbox;
    }

    private Notification createNotificationIfAbsent(
            ConsumerRecord<String, String> record,
            OrderNotificationEventPayload event,
            String eventId
    ) {
        Optional<Notification> existing = notificationRepository.findBySourceEventId(eventId);
        if (existing.isPresent()) {
            return null;
        }

        String recipientUserId = event.getUserId();
        if (recipientUserId == null || recipientUserId.isBlank()) {
            log.warn(
                    "Bỏ qua order notification event thiếu userId. eventId={}, eventType={}, orderId={}, orderCode={}, customerId={}, topic={}, key={}",
                    eventId,
                    event.getEventType(),
                    event.getOrderId(),
                    event.getOrderCode(),
                    event.getCustomerId(),
                    record.topic(),
                    record.key()
            );
            return null;
        }

        Notification notification = new Notification();
        notification.setUserId(recipientUserId);
        notification.setCustomerId(event.getCustomerId());
//        notification.setBranchId(event.getBranchId());
        notification.setType(event.getEventType());
        notification.setTitle(firstNonBlank(event.getTitle(), defaultTitle(event)));
        notification.setMessage(firstNonBlank(event.getMessage(), defaultMessage(event)));
        notification.setSourceEventId(eventId);
        notification.setSourceEventType(event.getEventType());
        notification.setAggregateType(metadataResolver.aggregateType(record));
        notification.setAggregateId(metadataResolver.aggregateId(record, event));
        notification.setOrderId(event.getOrderId());
        notification.setOrderCode(event.getOrderCode());
//        notification.setReturnId(event.getReturnId());
//        notification.setReturnCode(event.getReturnCode());
        notification.setPaymentId(event.getPaymentId());
//        notification.setRefundTransactionId(event.getRefundTransactionId());
//        notification.setActorUserId(event.getActorUserId());
        notification.setSagaId(event.getSagaId());
        notification.setStatus("UNREAD");

        return notificationRepository.saveAndFlush(notification);
    }

    private void sendAfterCommit(Notification notification) {
        if (notification == null || notification.getUserId() == null || notification.getUserId().isBlank()) {
            return;
        }

        NotificationResponse response = NotificationResponse.from(notification);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notificationSseService.sendToUser(notification.getUserId(), response);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationSseService.sendToUser(notification.getUserId(), response);
            }
        });
    }

    private void markProcessed(NotificationInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markIgnored(NotificationInbox inbox) {
        inbox.setStatus(InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markFailed(NotificationInbox inbox, Exception ex) {
        inbox.setStatus(InboxStatus.FAILED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(ex.getMessage());
    }

    private boolean isNotificationEvent(String eventType) {
        return "ORDER_COMPLETED".equals(eventType)
                || "ORDER_PAYMENT_SUCCEEDED".equals(eventType)
                || "ORDER_PAYMENT_FAILED".equals(eventType)
                || "ORDER_CONFIRMED".equals(eventType)
                || "ORDER_CANCELLED".equals(eventType)
                || "ORDER_STATUS_CHANGED".equals(eventType)
                || "RETURN_CREATED".equals(eventType)
                || "RETURN_RECEIVED".equals(eventType)
                || "RETURN_CANCELLED".equals(eventType)
                || "RETURN_REFUND_COMPLETED".equals(eventType)
                || "RETURN_COMPLETED".equals(eventType);
    }

    private boolean isTerminal(InboxStatus status) {
        return status == InboxStatus.PROCESSED
                || status == InboxStatus.IGNORED
                || status == InboxStatus.FAILED
                || status == InboxStatus.DEAD;
    }

    private String defaultTitle(OrderNotificationEventPayload event) {
        return switch (event.getEventType()) {
            case "ORDER_PAYMENT_SUCCEEDED", "ORDER_CONFIRMED" -> "Order confirmed";
            case "ORDER_PAYMENT_FAILED" -> "Payment failed";
            case "ORDER_CANCELLED" -> "Order cancelled";
            case "ORDER_STATUS_CHANGED" -> "Order status changed";
            case "RETURN_CREATED" -> "Phiếu hoàn hàng đã được tạo";
            case "RETURN_RECEIVED" -> "Đã nhận hàng trả";
            case "RETURN_CANCELLED" -> "Phiếu hoàn hàng đã bị hủy";
            case "RETURN_REFUND_COMPLETED" -> "Hoàn tiền thành công";
            case "RETURN_COMPLETED" -> "Xử lý hàng trả hoàn tất";
            default -> "Order notification";
        };
    }

    private String defaultMessage(OrderNotificationEventPayload event) {
        String orderCode = firstNonBlank(event.getOrderCode(), event.getOrderId());
        String returnCode = firstNonBlank(event.getReturnCode(), event.getReturnId());
        return switch (event.getEventType()) {
            case "ORDER_PAYMENT_SUCCEEDED", "ORDER_CONFIRMED" -> "Order " + orderCode + " has been confirmed.";
            case "ORDER_PAYMENT_FAILED" -> "Order " + orderCode + " payment failed.";
            case "ORDER_CANCELLED" -> "Order " + orderCode + " was cancelled.";
            case "ORDER_STATUS_CHANGED" -> "Order " + orderCode + " status changed to " + event.getNewStatus() + ".";
            case "RETURN_CREATED" -> "Phiếu hoàn hàng " + returnCode + " đã được tạo.";
            case "RETURN_RECEIVED" -> "Cửa hàng đã nhận hàng trả của phiếu " + returnCode + ".";
            case "RETURN_CANCELLED" -> "Phiếu hoàn hàng " + returnCode + " đã bị hủy.";
            case "RETURN_REFUND_COMPLETED" -> "Phiếu " + returnCode + " đã hoàn tiền thành công.";
            case "RETURN_COMPLETED" -> "Phiếu hoàn " + returnCode + " đã hoàn tất xử lý hàng. Không phát sinh hoàn tiền.";
            default -> "Order " + orderCode + " has a new update.";
        };
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value());
    }
}
