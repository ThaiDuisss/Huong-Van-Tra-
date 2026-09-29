package org.hvtra.orderservice.integration.inventory.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionFailedEvent;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionProcessedEvent;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnInventoryDispositionAckInboxService {

    private static final String PROCESSED_EVENT_TYPE = "RETURN_INVENTORY_DISPOSITION_PROCESSED";
    private static final String FAILED_EVENT_TYPE = "RETURN_INVENTORY_DISPOSITION_FAILED";
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;
    private final OrderInboxRepository orderInboxRepository;
    private final ReturnInventoryDispositionAckEventParser eventParser;
    private final EventMetadataResolver metadataResolver;
    private final OrderReturnService orderReturnService;

    @Value("${order.inventory.events.return-disposition-ack.group-id:order-return-inventory-ack-events}")
    private String consumerGroup;

    @Value("${order.inventory.events.topics.return-disposition-processed:inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED}")
    private String processedTopic;

    @Value("${order.inventory.events.topics.return-disposition-failed:inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED}")
    private String failedTopic;

    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        if (failedTopic.equals(record.topic())) {
            eventParser.parseFailedCreateEvent(record.value())
                    .ifPresent(event -> handleFailedRecord(record, event));
            return;
        }

        if (processedTopic.equals(record.topic())) {
            eventParser.parseProcessedCreateEvent(record.value())
                    .ifPresent(event -> handleProcessedRecord(record, event));
            return;
        }

        log.debug("Skip non-create or unrecognized inventory disposition ack event. topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
    }

    private void handleProcessedRecord(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionProcessedEvent event
    ) {
        normalizeMetadata(record, event, PROCESSED_EVENT_TYPE);
        String eventId = metadataResolver.resolveEventId(record, event, "Return inventory disposition processed event");

        OrderInbox inbox = claimInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            log.debug("Duplicate processed ack event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
            return;
        }

        inbox.setStatus(InboxStatus.PROCESSING);

        UUID commandUuid = parseUuid(event.getCommandId());
        if (commandUuid == null) {
            log.error("Processed ack missing or invalid commandId: {}. eventId={}", event.getCommandId(), eventId);
            markFailed(inbox, "Missing or invalid commandId: " + event.getCommandId());
            return;
        }

        Optional<OrderReturnInventoryCommand> cmdOpt =
                orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandUuid);
        if (cmdOpt.isEmpty()) {
            log.error("OrderReturnInventoryCommand not found for commandId={}. eventId={}", commandUuid, eventId);
            markFailed(inbox, "Command not found: " + commandUuid);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        if (!validateReturnId(command, event.getReturnId())) {
            markFailed(inbox, "ReturnId mismatch for commandId: " + commandUuid);
            return;
        }

        if (parseUuid(event.getInspectionId()) == null) {
            markFailed(inbox, "Processed acknowledgement is missing a valid inspectionId");
            return;
        }

        if (command.getStatus() == OrderReturnInventoryCommandStatus.PROCESSED) {
            log.debug("Duplicate processed ack event: command already PROCESSED. commandId={}, eventId={}",
                    command.getCommandId(), eventId);
            markProcessed(inbox);
            return;
        }

        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.warn("Cannot process ack: command not in PENDING status (current: {}). commandId={}, eventId={}",
                    command.getStatus(), command.getCommandId(), eventId);
            markProcessed(inbox);
            return;
        }

        UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;
        boolean beforePhysicalDone = returnId != null && orderReturnService.isPhysicalDispositionCompleted(returnId);

        applyProcessedAck(command, event);

        if (returnId != null) {
            boolean afterPhysicalDone = orderReturnService.isPhysicalDispositionCompleted(returnId);
            if (!beforePhysicalDone && afterPhysicalDone) {
                orderReturnService.recordPhysicalCompletion(returnId, command.getRequestedBy(), command.getInspectionId());
                orderReturnService.checkAndAutoCompleteNoRefundReturn(returnId);
            }
        }
        markProcessed(inbox);
    }

    private void handleFailedRecord(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionFailedEvent event
    ) {
        normalizeMetadata(record, event, FAILED_EVENT_TYPE);
        String eventId = metadataResolver.resolveEventId(record, event, "Return inventory disposition failed event");

        OrderInbox inbox = claimInbox(record, event, eventId);
        if (isTerminal(inbox.getStatus())) {
            log.debug("Duplicate failed ack event already handled. eventId={}, consumerGroup={}", eventId, consumerGroup);
            return;
        }

        inbox.setStatus(InboxStatus.PROCESSING);

        UUID commandUuid = parseUuid(event.getCommandId());
        if (commandUuid == null) {
            log.error("Failed ack missing or invalid commandId: {}. eventId={}", event.getCommandId(), eventId);
            markFailed(inbox, "Missing or invalid commandId: " + event.getCommandId());
            return;
        }

        Optional<OrderReturnInventoryCommand> cmdOpt =
                orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandUuid);
        if (cmdOpt.isEmpty()) {
            log.error("OrderReturnInventoryCommand not found for commandId={}. eventId={}", commandUuid, eventId);
            markFailed(inbox, "Command not found: " + commandUuid);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        if (!validateReturnId(command, event.getReturnId())) {
            markFailed(inbox, "ReturnId mismatch for commandId: " + commandUuid);
            return;
        }

        applyFailedAck(command, event);
        markProcessed(inbox);
    }

    private void applyProcessedAck(
            OrderReturnInventoryCommand command,
            ReturnInventoryDispositionProcessedEvent event
    ) {
        OrderReturnInventoryCommandStatus currentStatus = command.getStatus();
        if (currentStatus == OrderReturnInventoryCommandStatus.PENDING) {
            command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
            command.setProcessedAt(parseInstant(event.getProcessedAt()));
            command.setInspectionId(parseUuid(event.getInspectionId()));
            command.setFailedAt(null);
            command.setFailureCode(null);
            command.setFailureReason(null);
            orderReturnInventoryCommandRepository.saveAndFlush(command);
            log.info("OrderReturnInventoryCommand transitioned PENDING -> PROCESSED. commandId={}, inspectionId={}",
                    command.getCommandId(), event.getInspectionId());
        } else if (currentStatus == OrderReturnInventoryCommandStatus.PROCESSED) {
            log.debug("OrderReturnInventoryCommand already PROCESSED. commandId={}", command.getCommandId());
        } else if (currentStatus == OrderReturnInventoryCommandStatus.FAILED) {
            log.error("CRITICAL CONFLICT: Received PROCESSED ack for command already in FAILED status! commandId={}, eventId={}. Manual reconciliation required.",
                    command.getCommandId(), event.getEventId());
        }
    }

    private void applyFailedAck(
            OrderReturnInventoryCommand command,
            ReturnInventoryDispositionFailedEvent event
    ) {
        OrderReturnInventoryCommandStatus currentStatus = command.getStatus();
        if (currentStatus == OrderReturnInventoryCommandStatus.PENDING) {
            command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
            command.setFailedAt(parseInstant(event.getFailedAt()));
            command.setFailureCode(event.getFailureCode());
            command.setFailureReason(event.getFailureReason());
            orderReturnInventoryCommandRepository.saveAndFlush(command);
            log.info("OrderReturnInventoryCommand transitioned PENDING -> FAILED. commandId={}, failureCode={}, reason={}",
                    command.getCommandId(), event.getFailureCode(), event.getFailureReason());
        } else if (currentStatus == OrderReturnInventoryCommandStatus.FAILED) {
            log.debug("OrderReturnInventoryCommand already FAILED. commandId={}", command.getCommandId());
        } else if (currentStatus == OrderReturnInventoryCommandStatus.PROCESSED) {
            log.warn("CONFLICT: Received FAILED ack for command already in PROCESSED status! commandId={}, eventId={}. Ignored.",
                    command.getCommandId(), event.getEventId());
        }
    }

    private boolean validateReturnId(OrderReturnInventoryCommand command, String eventReturnId) {
        UUID expectedReturnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;
        UUID actualReturnId = parseUuid(eventReturnId);
        if (expectedReturnId == null || !expectedReturnId.equals(actualReturnId)) {
            log.error("ReturnId missing or mismatched in ack event. expected={}, actual={}, commandId={}",
                    expectedReturnId, eventReturnId, command.getCommandId());
            return false;
        }
        return true;
    }

    private OrderInbox claimInbox(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
            String eventId
    ) {
        Optional<OrderInbox> existing = orderInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
        if (existing.isPresent()) {
            return existing.get();
        }

        OrderInbox inbox = buildInbox(record, event, eventId);
        return orderInboxRepository.saveAndFlush(inbox);
    }

    private OrderInbox buildInbox(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
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
        inbox.setSourceService(metadataResolver.sourceService(record, "inventory-service"));
        inbox.setEntityType("ORDER_RETURN_INVENTORY_COMMAND");
        inbox.setEntityId(resolveEntityId(event, record.key()));
        inbox.setCorrelationId(metadataResolver.correlationId(record, record.key()));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.RECEIVED);
        inbox.setReceivedAt(Instant.now());
        return inbox;
    }

    private String resolveEntityId(CommonEventPayload event, String recordKey) {
        if (event instanceof ReturnInventoryDispositionProcessedEvent p && p.getCommandId() != null && !p.getCommandId().isBlank()) {
            return p.getCommandId().trim();
        }
        if (event instanceof ReturnInventoryDispositionFailedEvent f && f.getCommandId() != null && !f.getCommandId().isBlank()) {
            return f.getCommandId().trim();
        }
        return sanitizeUuid(recordKey);
    }

    private String sanitizeUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text.length() <= 36 ? text : text.substring(0, 36);
    }

    private void normalizeMetadata(
            ConsumerRecord<String, String> record,
            CommonEventPayload event,
            String expectedEventType
    ) {
        metadataResolver.normalizeBeforeEventId(
                record,
                event,
                topic -> expectedEventType,
                expectedEventType,
                "inventory ack event"
        );
        metadataResolver.normalizeEventKey(record, event);
    }

    private boolean isTerminal(InboxStatus status) {
        return status == InboxStatus.PROCESSED
                || status == InboxStatus.IGNORED
                || status == InboxStatus.FAILED
                || status == InboxStatus.DEAD;
    }

    private void markProcessed(OrderInbox inbox) {
        inbox.setStatus(InboxStatus.PROCESSED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(null);
        orderInboxRepository.saveAndFlush(inbox);
    }

    private void markFailed(OrderInbox inbox, String error) {
        inbox.setStatus(InboxStatus.FAILED);
        inbox.setProcessedAt(Instant.now());
        inbox.setLastError(error);
        orderInboxRepository.saveAndFlush(inbox);
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(text.trim());
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private UUID parseUuid(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
