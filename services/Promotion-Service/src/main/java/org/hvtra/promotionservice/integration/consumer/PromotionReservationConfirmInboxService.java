package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.kafka.inbox.InboxStatus;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.hvtra.promotionservice.entity.PromotionInbox;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionReservationConfirmInboxService {

    private static final String INVENTORY_RESERVATION_CONFIRM_REQUESTED = "INVENTORY_RESERVATION_CONFIRM_REQUESTED";
    private final CouponUsageService couponUsageService;

    private final OrderPromotionEventPayloadParser OrderPromotionEventPayloadParser;
    private final OrderPromotionEventMetadataResolver orderInventoryEventMetadataResolver;

    @Value("${kafka-consumer-config.group-id:Inventory-Service-order-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        Optional<OrderPromotionEventPayload> parsedEvent = OrderPromotionEventPayloadParser.parseCreateEvent(record.value());
        if (parsedEvent.isEmpty()) {
            log.debug("Bỏ qua order inventory CDC event không phải create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        OrderPromotionEventPayload event = parsedEvent.get();
        orderInventoryEventMetadataResolver.normalizeBeforeEventId(record, event);
        String eventId = orderInventoryEventMetadataResolver.resolveEventId(record, event);
        orderInventoryEventMetadataResolver.normalizeEventKey(record, event);

        PromotionInbox inbox = insertInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            return;
        }

        if (!INVENTORY_RESERVATION_CONFIRM_REQUESTED.equals(event.getEventType())) {
            log.debug("Bỏ qua order inventory event không phải confirm reservation: {}", event.getEventType());
            markIgnored(inbox);
            return;
        }

        try {
            inbox.setStatus(InboxStatus.PROCESSING);
            inventoryReservationService.confirmOrder(new ConfirmInventoryReservationOrderRequest(UUID.fromString(event.getOrderId())));
            markProcessed(inbox);
        } catch (BadRequestException ex) {
            if (!isMissingReservation(ex)) {
                throw ex;
            }
            log.error("Không confirm reservation vì order không có reservation active. eventId={}, orderId={}",
                    eventId, event.getOrderId(), ex);
            markFailed(inbox, ex);
        }
    }

    private PromotionInbox insertInbox(
            ConsumerRecord<String, String> record,
            OrderPromotionEventPayload event,
            String eventId
    ) {
        Optional<PromotionInbox> existing = PromotionInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            PromotionInbox inbox = existing.get();
            if (isTerminal(inbox.getStatus())) {
                log.debug("Duplicate inventory event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
                return inbox;
            }

            throw new IllegalStateException(
                    "Duplicate inventory event is not completed yet. eventId=" + eventId + ", status=" + inbox.getStatus()
            );
        }

        try {
            PromotionInbox inbox = buildInbox(record, event, eventId);
            return PromotionInboxRepository.saveAndFlush(inbox);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    private PromotionInbox buildInbox(
            ConsumerRecord<String, String> record,
            OrderPromotionEventPayload event,
            String eventId
    ) {
        PromotionInbox inbox = new PromotionInbox();

        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(event.getEventType());
        inbox.setEventVersion(1);
        inbox.setSourceService(orderInventoryEventMetadataResolver.sourceService(record));
        inbox.setEntityType("ORDER");
        inbox.setEntityId(event.getOrderId());
        inbox.setCorrelationId(orderInventoryEventMetadataResolver.correlationId(record, event));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(orderInventoryEventMetadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);

        return inbox;
    }

    private void markProcessed(PromotionInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markIgnored(PromotionInbox inbox) {
        inbox.setStatus(InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markFailed(PromotionInbox inbox, Exception ex) {
        inbox.setStatus(InboxStatus.FAILED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(ex.getMessage());
    }

    private boolean isTerminal(InboxStatus status) {
        return status == InboxStatus.PROCESSED
                || status == InboxStatus.IGNORED
                || status == InboxStatus.FAILED
                || status == InboxStatus.DEAD;
    }

    private boolean isMissingReservation(BadRequestException ex) {
        return ex.getMessage() != null && ex.getMessage().contains("No active reservation found");
    }
}
