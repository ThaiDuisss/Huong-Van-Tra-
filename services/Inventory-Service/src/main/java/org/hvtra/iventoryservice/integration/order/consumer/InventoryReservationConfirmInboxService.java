package org.hvtra.iventoryservice.integration.order.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.integration.order.dto.OrderInventoryEventPayload;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
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
public class InventoryReservationConfirmInboxService {

    private static final String INVENTORY_RESERVATION_CONFIRM_REQUESTED = "INVENTORY_RESERVATION_CONFIRM_REQUESTED";

    private final InventoryReservationService inventoryReservationService;
    private final InventoryInboxRepository inventoryInboxRepository;
    private final OrderInventoryEventPayloadParser orderInventoryEventPayloadParser;
    private final OrderInventoryEventMetadataResolver orderInventoryEventMetadataResolver;

    @Value("${kafka-consumer-config.group-id:Inventory-Service-order-events}")
    private String consumerGroup;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        Optional<OrderInventoryEventPayload> parsedEvent = orderInventoryEventPayloadParser.parseCreateEvent(record.value());
        if (parsedEvent.isEmpty()) {
            log.debug("Bỏ qua order inventory CDC event không phải create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        OrderInventoryEventPayload event = parsedEvent.get();
        orderInventoryEventMetadataResolver.normalizeBeforeEventId(record, event);
        String eventId = orderInventoryEventMetadataResolver.resolveEventId(record, event);
        orderInventoryEventMetadataResolver.normalizeEventKey(record, event);

        InventoryInbox inbox = insertInbox(record, event, eventId);
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

    private InventoryInbox insertInbox(
            ConsumerRecord<String, String> record,
            OrderInventoryEventPayload event,
            String eventId
    ) {
        Optional<InventoryInbox> existing = inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            InventoryInbox inbox = existing.get();
            if (isTerminal(inbox.getStatus())) {
                log.debug("Duplicate inventory event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
                return inbox;
            }

            throw new IllegalStateException(
                    "Duplicate inventory event is not completed yet. eventId=" + eventId + ", status=" + inbox.getStatus()
            );
        }

        try {
            InventoryInbox inbox = buildInbox(record, event, eventId);
            return inventoryInboxRepository.saveAndFlush(inbox);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    private InventoryInbox buildInbox(
            ConsumerRecord<String, String> record,
            OrderInventoryEventPayload event,
            String eventId
    ) {
        InventoryInbox inbox = new InventoryInbox();

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

    private void markProcessed(InventoryInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markIgnored(InventoryInbox inbox) {
        inbox.setStatus(InboxStatus.IGNORED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
    }

    private void markFailed(InventoryInbox inbox, Exception ex) {
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
