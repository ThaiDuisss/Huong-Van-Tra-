package org.hvtra.orderservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSucceededInboxService {

    private final OrderService orderService;
    private final OrderInboxRepository orderInboxRepository;
    private final OrderRepository orderRepository;
    private final PaymentEventPayloadParser paymentEventPayloadParser;
    private final PaymentEventMetadataResolver paymentEventMetadataResolver;

    @Value("${kafka-consumer-config.group-id:Order-Service-payment-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        Optional<PaymentOrderEventPayload> parsedEvent = paymentEventPayloadParser.parseCreateEvent(record.value());
        if (parsedEvent.isEmpty()) {
            log.debug("Bỏ qua payment CDC event không phải create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        PaymentOrderEventPayload event = parsedEvent.get();
        paymentEventMetadataResolver.normalizeBeforeEventId(record, event);
        String eventId = paymentEventMetadataResolver.resolveEventId(record, event);
        paymentEventMetadataResolver.normalizeEventKey(record, event);
        paymentEventMetadataResolver.normalizeSagaId(record, event);

        OrderInbox inbox = insertInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            if (inbox.getStatus() == InboxStatus.PROCESSED) {
                retryPaidInventoryIfNeeded(event);
            }
            return;
        }

        inbox.setStatus(InboxStatus.PROCESSING);
        orderService.handlePaymentEvent(event);
        markProcessed(inbox);
    }

    private OrderInbox insertInbox(
            ConsumerRecord<String, String> record,
            PaymentOrderEventPayload event,
            String eventId
    ) {
        Optional<OrderInbox> existing = orderInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            OrderInbox inbox = existing.get();
            if (isTerminal(inbox.getStatus())) {
                log.debug("Duplicate payment event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
                return inbox;
            }

            throw new IllegalStateException(
                    "Duplicate payment event is not completed yet. eventId=" + eventId + ", status=" + inbox.getStatus()
            );
        }

        try {
            OrderInbox inbox = buildInbox(record, event, eventId);
            return orderInboxRepository.saveAndFlush(inbox);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    private OrderInbox buildInbox(
            ConsumerRecord<String, String> record,
            PaymentOrderEventPayload event,
            String eventId
    ) {
        OrderInbox inbox = new OrderInbox();

        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(paymentEventMetadataResolver.sourceService(record));
        inbox.setEntityType("PAYMENT");
        inbox.setEntityId(event.getPaymentId());
        inbox.setSagaId(event.getSagaId());
        inbox.setCorrelationId(paymentEventMetadataResolver.correlationId(record));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(paymentEventMetadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);

        return inbox;
    }

    private void markProcessed(OrderInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void retryPaidInventoryIfNeeded(PaymentOrderEventPayload event) {
        if (event.getEventType() == null
                || !event.getEventType().toUpperCase(Locale.ROOT).endsWith("SUCCEEDED")
                || event.getOrderId() == null) {
            return;
        }
        orderRepository.findByIdAndIsDeletedFalse(UUID.fromString(event.getOrderId())).ifPresent(order -> {
            OrderInventoryProcessingStatus status = order.getInventoryProcessingStatus();
            if (order.getPaymentStatus() == PaymentStatus.PAID
                    && (status == null || status == OrderInventoryProcessingStatus.NOT_STARTED
                    || status == OrderInventoryProcessingStatus.FAILED)) {
                orderService.handlePaymentEvent(event);
            }
        });
    }

    private void markIgnored(OrderInbox inbox) {
        inbox.setStatus(InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private boolean isTerminal(InboxStatus status) {
        return status == InboxStatus.PROCESSED
                || status == InboxStatus.IGNORED
                || status == InboxStatus.FAILED
                || status == InboxStatus.DEAD;
    }
}
