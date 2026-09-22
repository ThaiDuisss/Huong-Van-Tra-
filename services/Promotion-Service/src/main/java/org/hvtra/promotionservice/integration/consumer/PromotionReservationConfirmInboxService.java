package org.hvtra.promotionservice.integration.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.promotionservice.dto.event.OrderPromotionEventPayload;
import org.hvtra.promotionservice.entity.PromotionInbox;
import org.hvtra.promotionservice.repository.PromotionInboxRepository;
import org.hvtra.promotionservice.service.CouponUsageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionReservationConfirmInboxService {

    private static final String PROMOTION_USAGE_COMMIT_REQUESTED = "PROMOTION_USAGE_COMMIT_REQUESTED";
    private final CouponUsageService couponUsageService;

    private final OrderPromotionEventPayloadParser orderPromotionEventPayloadParser;
    private final OrderPromotionEventMetadataResolver orderPromotionEventMetadataResolver;
    private final PromotionInboxRepository promotionInboxRepository;


    @Value("${kafka-consumer-config.group-id:Promotion-Service-order-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        Optional<OrderPromotionEventPayload> parsedEvent = orderPromotionEventPayloadParser.parseCreateEvent(record.value());
        if (parsedEvent.isEmpty()) {
            log.debug("Bỏ qua order promotion CDC event không phải create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        OrderPromotionEventPayload event = parsedEvent.get();
        orderPromotionEventMetadataResolver.normalizeBeforeEventId(record, event);
        String eventId = orderPromotionEventMetadataResolver.resolveEventId(record, event);
        orderPromotionEventMetadataResolver.normalizeEventKey(record, event);

        PromotionInbox inbox = insertInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            return;
        }

        if (!PROMOTION_USAGE_COMMIT_REQUESTED.equals(event.getEventType())) {
            log.debug("Bỏ qua order promotion event không phải commit coupon usage: {}", event.getEventType());
            markIgnored(inbox);
            return;
        }

        try {
            inbox.setStatus(InboxStatus.PROCESSING);
            applyCouponUsages(event);
            markProcessed(inbox);
        } catch (RuntimeException ex) {
            log.error("Không commit được coupon usage. eventId={}, orderId={}",
                    eventId, event.getOrderId(), ex);
            markFailed(inbox, ex);
            throw ex;
        }
    }

    private PromotionInbox insertInbox(
            ConsumerRecord<String, String> record,
            OrderPromotionEventPayload event,
            String eventId
    ) {
        Optional<PromotionInbox> existing = promotionInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
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
            return promotionInboxRepository.saveAndFlush(inbox);
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
        inbox.setSourceService(orderPromotionEventMetadataResolver.sourceService(record));
        inbox.setEntityType("ORDER");
        inbox.setEntityId(event.getOrderId());
        inbox.setCorrelationId(orderPromotionEventMetadataResolver.correlationId(record, event));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(orderPromotionEventMetadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);

        return inbox;
    }

    private void applyCouponUsages(OrderPromotionEventPayload event) {
        List<String> usageIds = event.getUsageIds();
        if (usageIds == null || usageIds.isEmpty()) {
            throw new IllegalArgumentException("Promotion commit event thiếu usageIds");
        }

        for (String rawUsageId : usageIds) {
            if (rawUsageId == null || rawUsageId.isBlank()) {
                continue;
            }

            UUID usageId = UUID.fromString(rawUsageId);
            couponUsageService.applyUsage(usageId);
        }
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

}
