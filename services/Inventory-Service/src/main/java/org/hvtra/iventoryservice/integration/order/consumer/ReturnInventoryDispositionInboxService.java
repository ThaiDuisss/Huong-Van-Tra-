package org.hvtra.iventoryservice.integration.order.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.kafka.inbox.InboxStatus;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionLineRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.service.CustomerReturnInspectionService;
import org.hvtra.iventoryservice.domain.entity.InventoryInbox;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.integration.order.dto.ReturnInventoryDispositionRequestedEvent;
import org.hvtra.iventoryservice.integration.order.dto.ReturnInventoryDispositionRequestedLine;
import org.hvtra.iventoryservice.integration.order.repository.InventoryInboxRepository;
import org.hvtra.iventoryservice.outbox.publisher.InventoryOutboxPublisher;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnInventoryDispositionInboxService {

    private static final String EVENT_TYPE = "RETURN_INVENTORY_DISPOSITION_REQUESTED";
    private static final String TOPIC = "order.event.RETURN_INVENTORY_DISPOSITION_REQUESTED";
    private static final String UNKNOWN_EVENT_TYPE = "UNKNOWN_ORDER_EVENT";

    private final CustomerReturnInspectionService customerReturnInspectionService;
    private final InventoryInboxRepository inventoryInboxRepository;
    private final ReturnInventoryDispositionRequestedEventParser eventParser;
    private final EventMetadataResolver metadataResolver;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final InventoryOutboxPublisher inventoryOutboxPublisher;

    @Value("${inventory.order.events.return-disposition.group-id:inventory-return-disposition}")
    private String consumerGroup;

    public void handle(ConsumerRecord<String, String> record) {
        Optional<ReturnInventoryDispositionRequestedEvent> parsedEvent;
        try {
            parsedEvent = eventParser.parseCreateEvent(record.value());
        } catch (IllegalArgumentException ex) {
            log.error("Malformed return inventory disposition event. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), ex);
            markMalformedFailed(record, ex);
            return;
        }

        if (parsedEvent.isEmpty()) {
            log.debug("Skip return inventory disposition CDC event that is not create. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        ReturnInventoryDispositionRequestedEvent event = parsedEvent.get();
        normalizeMetadata(record, event);
        String eventId = metadataResolver.resolveEventId(record, event, "Return inventory disposition event");
        String payloadHash = businessPayloadHash(event);

        try {
            transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(status ->
                    process(record, event, eventId, payloadHash)
            );
        } catch (RuntimeException ex) {
            if (!isPermanentFailure(ex)) {
                throw ex;
            }
            log.warn("Permanent return inventory disposition failure. commandId={}, eventId={}, returnId={}, reason={}",
                    event.getCommandId(), eventId, event.getReturnId(), ex.getMessage());
            markFailed(record, event, eventId, payloadHash, ex);
        }
    }

    private void process(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionRequestedEvent event,
            String eventId,
            String payloadHash
    ) {
        String commandId = requiredText(event.getCommandId(), "commandId");
        if (commandId.length() > 36) {
            throw new BadRequestException("commandId length must not exceed 36 characters");
        }

        InventoryInbox inbox = claimInbox(record, event, eventId, payloadHash, commandId);
        if (isTerminal(inbox.getStatus())) {
            handleTerminalDuplicate(inbox, event, eventId, payloadHash);
            return;
        }

        if (!EVENT_TYPE.equals(event.getEventType())) {
            log.debug("Skip order event that is not return inventory disposition. eventType={}, eventId={}",
                    event.getEventType(), eventId);
            markIgnored(inbox);
            return;
        }

        inbox.setStatus(InboxStatus.PROCESSING);
        UUID returnId = parseRequiredUuid(event.getReturnId(), "returnId");
        parseRequiredUuid(event.getCommandId(), "commandId");
        CreateCustomerReturnInspectionRequest request = toInspectionRequest(event);

        CustomerReturnInspectionResponse createdInspection =
                customerReturnInspectionService.createInspection(returnId, request);
        CustomerReturnInspectionResponse postedInspection =
                customerReturnInspectionService.postInspection(createdInspection.id());

        markProcessed(inbox);
        inventoryOutboxPublisher.publishReturnInventoryDispositionProcessed(
                commandId,
                returnId.toString(),
                postedInspection.id().toString(),
                event.getSagaId(),
                event.getCorrelationId()
        );
        log.info("Processed return inventory disposition. commandId={}, eventId={}, returnId={}, inspectionId={}",
                commandId, eventId, returnId, postedInspection.id());
    }

    private InventoryInbox claimInbox(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionRequestedEvent event,
            String eventId,
            String payloadHash,
            String commandId
    ) {
        Optional<InventoryInbox> existing = inventoryInboxRepository
                .findByCommandIdAndConsumerGroupForUpdate(commandId, consumerGroup);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return inventoryInboxRepository.saveAndFlush(buildInbox(
                    record,
                    event,
                    eventId,
                    payloadHash,
                    commandId,
                    InboxStatus.RECEIVED,
                    null
            ));
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    private void handleTerminalDuplicate(
            InventoryInbox inbox,
            ReturnInventoryDispositionRequestedEvent event,
            String eventId,
            String payloadHash
    ) {
        if (hasText(inbox.getBusinessPayloadHash())
                && hasText(payloadHash)
                && !inbox.getBusinessPayloadHash().equals(payloadHash)) {
            String message = "Duplicate commandId delivered with conflicting payload. commandId="
                    + inbox.getCommandId() + ", eventId=" + eventId;
            inbox.setLastError(message);
            log.error("{} existingStatus={}, returnId={}", message, inbox.getStatus(), event.getReturnId());
            return;
        }

        log.debug("Duplicate return inventory disposition already terminal. commandId={}, status={}",
                inbox.getCommandId(), inbox.getStatus());
    }

    private CreateCustomerReturnInspectionRequest toInspectionRequest(ReturnInventoryDispositionRequestedEvent event) {
        if (event.getLines() == null || event.getLines().isEmpty()) {
            throw new BadRequestException("lines must not be empty");
        }

        List<CreateCustomerReturnInspectionLineRequest> lines = new ArrayList<>();
        for (ReturnInventoryDispositionRequestedLine line : event.getLines()) {
            validateRequiredLineSnapshot(line);
            lines.add(new CreateCustomerReturnInspectionLineRequest(
                    parseRequiredUuid(line.getReturnItemId(), "returnItemId"),
                    parseRequiredQuantity(line.getQuantity()),
                    parseRequiredResult(line.getResult()),
                    parseOptionalUuid(line.getDestinationLocationId(), "destinationLocationId"),
                    line.getNote()
            ));
        }

        return new CreateCustomerReturnInspectionRequest(null, lines);
    }

    private void validateRequiredLineSnapshot(ReturnInventoryDispositionRequestedLine line) {
        if (line == null) {
            throw new BadRequestException("line must not be null");
        }
        parseRequiredUuid(line.getOrderItemId(), "orderItemId");
        parseRequiredUuid(line.getProductVariantId(), "productVariantId");
    }

    private UUID parseRequiredUuid(String value, String fieldName) {
        String text = requiredText(value, fieldName);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(fieldName + " must be a valid UUID");
        }
    }

    private UUID parseOptionalUuid(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(fieldName + " must be a valid UUID");
        }
    }

    private BigDecimal parseRequiredQuantity(String value) {
        String text = requiredText(value, "quantity");
        try {
            BigDecimal quantity = new BigDecimal(text);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("quantity must be greater than 0");
            }
            return quantity;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("quantity must be numeric");
        }
    }

    private CustomerReturnQcResult parseRequiredResult(String value) {
        String text = requiredText(value, "result");
        try {
            return CustomerReturnQcResult.valueOf(text);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("result is not supported: " + text);
        }
    }

    private String requiredText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private void markMalformedFailed(ConsumerRecord<String, String> record, RuntimeException ex) {
        transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> {
            String eventId = eventIdFallback(record);
            Optional<InventoryInbox> existing = inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup);
            if (existing.isPresent() && isTerminal(existing.get().getStatus())) {
                return;
            }

            InventoryInbox inbox = existing.orElseGet(() -> buildMalformedInbox(record, eventId));
            markFailed(inbox, ex);
            inventoryInboxRepository.saveAndFlush(inbox);
        });
    }

    private void markFailed(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionRequestedEvent event,
            String eventId,
            String payloadHash,
            RuntimeException ex
    ) {
        transactionTemplate(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> {
            String commandId = hasText(event.getCommandId()) && event.getCommandId().length() <= 36
                    ? event.getCommandId().trim()
                    : null;
            Optional<InventoryInbox> existing = commandId == null
                    ? inventoryInboxRepository.findByEventIdAndConsumerGroup(eventId, consumerGroup)
                    : inventoryInboxRepository.findByCommandIdAndConsumerGroupForUpdate(commandId, consumerGroup);

            if (existing.isPresent()) {
                InventoryInbox inbox = existing.get();
                if (isTerminal(inbox.getStatus())) {
                    handleTerminalDuplicate(inbox, event, eventId, payloadHash);
                    return;
                }
                markFailed(inbox, ex);
                inventoryInboxRepository.saveAndFlush(inbox);
            } else {
                InventoryInbox inbox = buildInbox(
                        record,
                        event,
                        eventId,
                        payloadHash,
                        commandId,
                        InboxStatus.FAILED,
                        ex.getMessage()
                );
                inbox.setProcessedAt(Instant.now());
                inventoryInboxRepository.saveAndFlush(inbox);
            }

            if (commandId != null) {
                String failureCode = resolveFailureCode(ex);
                String failureReason = safeFailureReason(ex);
                inventoryOutboxPublisher.publishReturnInventoryDispositionFailed(
                        commandId,
                        event.getReturnId(),
                        failureCode,
                        failureReason,
                        event.getSagaId(),
                        event.getCorrelationId()
                );
            }
        });
    }

    private InventoryInbox buildInbox(
            ConsumerRecord<String, String> record,
            ReturnInventoryDispositionRequestedEvent event,
            String eventId,
            String payloadHash,
            String commandId,
            InboxStatus status,
            String error
    ) {
        InventoryInbox inbox = new InventoryInbox();

        inbox.setEventId(eventId);
        inbox.setCommandId(commandId);
        inbox.setBusinessPayloadHash(payloadHash);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(firstNonBlank(event.getEventType(), UNKNOWN_EVENT_TYPE));
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record, "order-service"));
        inbox.setEntityType("ORDER_RETURN_INVENTORY_COMMAND");
        inbox.setEntityId(firstNonBlank(commandId, event.getReturnId()));
        inbox.setSagaId(event.getSagaId());
        inbox.setCorrelationId(firstNonBlank(
                event.getCorrelationId(),
                metadataResolver.correlationId(record, event.getReturnId())
        ));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(status);
        inbox.setLastError(error);

        return inbox;
    }

    private InventoryInbox buildMalformedInbox(ConsumerRecord<String, String> record, String eventId) {
        InventoryInbox inbox = new InventoryInbox();

        inbox.setEventId(eventId);
        inbox.setConsumerGroup(consumerGroup);
        inbox.setTopic(record.topic());
        inbox.setPartitionNo(record.partition());
        inbox.setOffsetNo(record.offset());
        inbox.setMessageKey(record.key());
        inbox.setEventType(UNKNOWN_EVENT_TYPE);
        inbox.setEventVersion(1);
        inbox.setSourceService(metadataResolver.sourceService(record, "order-service"));
        inbox.setEntityType("ORDER_RETURN_INVENTORY_COMMAND");
        inbox.setEntityId(record.key());
        inbox.setCorrelationId(metadataResolver.correlationId(record, record.key()));
        inbox.setPayloadJson(record.value());
        inbox.setHeadersJson(metadataResolver.serializeHeaders(record));
        inbox.setStatus(InboxStatus.FAILED);

        return inbox;
    }

    private void normalizeMetadata(ConsumerRecord<String, String> record, ReturnInventoryDispositionRequestedEvent event) {
        metadataResolver.normalizeBeforeEventId(
                record,
                event,
                this::eventTypeFromTopic,
                UNKNOWN_EVENT_TYPE,
                "return inventory disposition event"
        );
        metadataResolver.normalizeEventKey(record, event);
    }

    private String eventTypeFromTopic(String topic) {
        if (TOPIC.equals(topic)) {
            return EVENT_TYPE;
        }
        return null;
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

    private boolean isPermanentFailure(RuntimeException ex) {
        return ex instanceof BadRequestException
                || ex instanceof ResourceNotFoundException
                || ex instanceof IllegalArgumentException;
    }

    private String resolveFailureCode(RuntimeException ex) {
        if (ex instanceof BadRequestException) {
            return "BAD_REQUEST";
        }
        if (ex instanceof ResourceNotFoundException) {
            return "RESOURCE_NOT_FOUND";
        }
        if (ex instanceof IllegalArgumentException) {
            return "ILLEGAL_ARGUMENT";
        }
        return "BUSINESS_ERROR";
    }

    private String safeFailureReason(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Internal business validation failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String businessPayloadHash(ReturnInventoryDispositionRequestedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", trimToNull(event.getCommandId()));
        payload.put("returnId", trimToNull(event.getReturnId()));
        payload.put("returnCode", trimToNull(event.getReturnCode()));
        payload.put("orderId", trimToNull(event.getOrderId()));
        payload.put("orderCode", trimToNull(event.getOrderCode()));
        payload.put("branchId", trimToNull(event.getBranchId()));
        payload.put("status", trimToNull(event.getStatus()));
        payload.put("requestedBy", trimToNull(event.getRequestedBy()));
        payload.put("lines", canonicalLines(event.getLines()));

        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot hash return inventory disposition payload", ex);
        }
    }

    private List<Map<String, Object>> canonicalLines(List<ReturnInventoryDispositionRequestedLine> lines) {
        if (lines == null) {
            return List.of();
        }

        return lines.stream()
                .map(line -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    if (line == null) {
                        return payload;
                    }
                    payload.put("returnItemId", trimToNull(line.getReturnItemId()));
                    payload.put("orderItemId", trimToNull(line.getOrderItemId()));
                    payload.put("productVariantId", trimToNull(line.getProductVariantId()));
                    payload.put("quantity", trimToNull(line.getQuantity()));
                    payload.put("result", trimToNull(line.getResult()));
                    payload.put("destinationLocationId", trimToNull(line.getDestinationLocationId()));
                    payload.put("note", trimToNull(line.getNote()));
                    return payload;
                })
                .toList();
    }

    private String eventIdFallback(ConsumerRecord<String, String> record) {
        String headerEventId = metadataResolver.headerValue(record, null, "event_id", "eventId");
        if (hasText(headerEventId)) {
            return headerEventId;
        }
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    private TransactionTemplate transactionTemplate(int propagationBehavior) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagationBehavior);
        return template;
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
