package org.hvtra.orderservice.integration.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.hvtra.commonservice.common_core.kafka.event.DebeziumOutboxEventPayloadParser;
import org.hvtra.commonservice.common_core.kafka.event.EventMetadataResolver;
import org.hvtra.commonservice.common_core.kafka.inbox.InboxStatus;
import org.hvtra.orderservice.domain.entity.OrderInbox;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionFailedEvent;
import org.hvtra.orderservice.integration.inventory.dto.ReturnInventoryDispositionProcessedEvent;
import org.hvtra.orderservice.repository.OrderInboxRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnInventoryDispositionAckInboxServiceTest {

    @Mock
    private OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;

    @Mock
    private OrderInboxRepository orderInboxRepository;

    @Mock
    private ReturnInventoryDispositionAckEventParser eventParser;

    @Mock
    private EventMetadataResolver metadataResolver;

    @Mock
    private OrderReturnService orderReturnService;

    @InjectMocks
    private ReturnInventoryDispositionAckInboxService ackInboxService;

    private UUID commandId;
    private UUID returnId;
    private UUID inspectionId;
    private UUID actorUserId;
    private OrderReturn orderReturn;
    private OrderReturnInventoryCommand command;
    private ConsumerRecord<String, String> record;
    private ReturnInventoryDispositionProcessedEvent processedEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ackInboxService, "consumerGroup", "test-consumer-group");
        ReflectionTestUtils.setField(ackInboxService, "processedTopic", "inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED");
        ReflectionTestUtils.setField(ackInboxService, "failedTopic", "inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED");

        commandId = UUID.randomUUID();
        returnId = UUID.randomUUID();
        inspectionId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();

        orderReturn = OrderReturn.builder()
                .returnCode("RET-001")
                .status(ReturnStatus.RECEIVED)
                .refundRequired(true)
                .build();
        orderReturn.setId(returnId);

        command = OrderReturnInventoryCommand.builder()
                .commandId(commandId)
                .orderReturn(orderReturn)
                .status(OrderReturnInventoryCommandStatus.PENDING)
                .requestedBy(actorUserId)
                .build();

        record = new ConsumerRecord<>("inventory.event.RETURN_INVENTORY_DISPOSITION_PROCESSED", 0, 0L, commandId.toString(), "{}");

        processedEvent = new ReturnInventoryDispositionProcessedEvent();
        processedEvent.setEventId("evt-1");
        processedEvent.setEventType("RETURN_INVENTORY_DISPOSITION_PROCESSED");
        processedEvent.setCommandId(commandId.toString());
        processedEvent.setReturnId(returnId.toString());
        processedEvent.setInspectionId(inspectionId.toString());
        processedEvent.setProcessedAt(Instant.now().toString());
    }

    @Test
    void handle_processedAck_whenCommandPendingAndTransitionsToPhysicalDone_recordsQcCompletedAndSkipsAutoCompleteIfRefundRequired() {
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        when(orderReturnService.isPhysicalDispositionCompleted(returnId))
                .thenReturn(false)  // before transition
                .thenReturn(true);  // after transition

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        assertEquals(inspectionId, command.getInspectionId());
        verify(orderReturnInventoryCommandRepository).saveAndFlush(command);

        verify(orderReturnService).recordPhysicalCompletion(eq(returnId), eq(actorUserId), eq(inspectionId));
        verify(orderReturnService).checkAndAutoCompleteNoRefundReturn(returnId);
    }

    @Test
    void handle_processedAck_whenCommandPendingAndAutoCompleteCalledForNoRefundReturn() {
        orderReturn.setRefundRequired(false);

        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        when(orderReturnService.isPhysicalDispositionCompleted(returnId))
                .thenReturn(false)
                .thenReturn(true);

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        verify(orderReturnService).recordPhysicalCompletion(eq(returnId), eq(actorUserId), eq(inspectionId));
        verify(orderReturnService).checkAndAutoCompleteNoRefundReturn(returnId);
    }

    @Test
    void handle_processedAck_whenCommandPendingAndRemainsPhysicalNotDone_doesNotRecordQcCompleted() {
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        when(orderReturnService.isPhysicalDispositionCompleted(returnId))
                .thenReturn(false)  // before transition
                .thenReturn(false); // after transition (partial command)

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        verify(orderReturnInventoryCommandRepository).saveAndFlush(command);

        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
        verify(orderReturnService, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    void handle_processedAck_whenCommandAlreadyProcessed_isIdempotentAndSkipsProcessing() {
        command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);

        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        verify(orderReturnInventoryCommandRepository, never()).saveAndFlush(command);
        verify(orderReturnService, never()).isPhysicalDispositionCompleted(any());
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
        verify(orderReturnService, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    void handle_processedAck_whenCommandNotFound_marksInboxFailed() {
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.empty());

        ackInboxService.handle(record);

        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
        verify(orderReturnService, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    void handle_processedAck_whenReturnIdMismatch_marksInboxFailed() {
        processedEvent.setReturnId(UUID.randomUUID().toString());

        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
        verify(orderReturnService, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    void handle_processedAck_withoutReturnId_doesNotProcessCommand() {
        processedEvent.setReturnId(null);
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-missing-return");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-missing-return"), any()))
                .thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        verify(orderReturnInventoryCommandRepository, never()).saveAndFlush(command);
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    void parser_doesNotTreatFailedEventAsProcessed() {
        ReturnInventoryDispositionAckEventParser realParser = new ReturnInventoryDispositionAckEventParser(
                new DebeziumOutboxEventPayloadParser(new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))
        );
        String payload = """
                {"eventType":"RETURN_INVENTORY_DISPOSITION_FAILED","commandId":"%s",
                 "returnId":"%s","failureCode":"BAD_REQUEST"}
                """.formatted(commandId, returnId);

        assertTrue(realParser.parseProcessedCreateEvent(payload).isEmpty());
        assertTrue(realParser.parseFailedCreateEvent(payload).isPresent());
    }

    @Test
    void parser_rejectsUnknownAndMissingEventType() {
        ReturnInventoryDispositionAckEventParser realParser = new ReturnInventoryDispositionAckEventParser(
                new DebeziumOutboxEventPayloadParser(new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))
        );
        String unknownType = """
                {"eventType":"RETURN_INVENTORY_DISPOSITION_UNKNOWN","commandId":"%s",
                 "returnId":"%s","inspectionId":"%s"}
                """.formatted(commandId, returnId, inspectionId);
        String missingType = """
                {"commandId":"%s","returnId":"%s","inspectionId":"%s"}
                """.formatted(commandId, returnId, inspectionId);
        for (String payload : new String[]{unknownType, missingType}) {
            assertTrue(realParser.parseProcessedCreateEvent(payload).isEmpty());
            assertTrue(realParser.parseFailedCreateEvent(payload).isEmpty());
        }
    }

    @Test
    void handle_processedAck_withoutInspectionId_doesNotCompletePhysicalReturn() {
        processedEvent.setInspectionId(null);
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        verify(orderReturnInventoryCommandRepository, never()).saveAndFlush(command);
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    void handle_failedAck_transitionsPendingCommandToFailed() {
        record = new ConsumerRecord<>("inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED", 0, 0L,
                commandId.toString(), "{}");
        ReturnInventoryDispositionFailedEvent failedEvent = new ReturnInventoryDispositionFailedEvent();
        failedEvent.setEventId("evt-failed-1");
        failedEvent.setEventType("RETURN_INVENTORY_DISPOSITION_FAILED");
        failedEvent.setCommandId(commandId.toString());
        failedEvent.setReturnId(returnId.toString());
        failedEvent.setFailureCode("STOCK_REJECTED");
        failedEvent.setFailureReason("Damage too severe");
        failedEvent.setFailedAt(Instant.now().toString());

        when(eventParser.parseFailedCreateEvent(record.value())).thenReturn(Optional.of(failedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-failed-1");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-failed-1"), any())).thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));

        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.FAILED, command.getStatus());
        assertEquals("STOCK_REJECTED", command.getFailureCode());
        assertEquals("Damage too severe", command.getFailureReason());
        verify(orderReturnInventoryCommandRepository).saveAndFlush(command);
        verify(eventParser, never()).parseProcessedCreateEvent(record.value());
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    void handle_lateProcessedAck_afterFailure_doesNotOverwriteCommand() {
        command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
        when(eventParser.parseProcessedCreateEvent(record.value())).thenReturn(Optional.of(processedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-late-success");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-late-success"), any()))
                .thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.FAILED, command.getStatus());
        assertNull(command.getInspectionId());
        verify(orderReturnInventoryCommandRepository, never()).saveAndFlush(command);
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    void handle_duplicateFailedAck_doesNotRewriteTerminalCommand() {
        command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
        command.setFailureCode("BAD_REQUEST");
        record = new ConsumerRecord<>("inventory.event.RETURN_INVENTORY_DISPOSITION_FAILED", 0, 1L,
                commandId.toString(), "{}");
        ReturnInventoryDispositionFailedEvent failedEvent = new ReturnInventoryDispositionFailedEvent();
        failedEvent.setEventType("RETURN_INVENTORY_DISPOSITION_FAILED");
        failedEvent.setCommandId(commandId.toString());
        failedEvent.setReturnId(returnId.toString());
        failedEvent.setFailureCode("STALE_FAILURE");

        when(eventParser.parseFailedCreateEvent(record.value())).thenReturn(Optional.of(failedEvent));
        when(metadataResolver.resolveEventId(any(), any(), any())).thenReturn("evt-duplicate-failure");
        when(orderInboxRepository.findByEventIdAndConsumerGroup(eq("evt-duplicate-failure"), any()))
                .thenReturn(Optional.empty());
        when(orderInboxRepository.saveAndFlush(any(OrderInbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderReturnInventoryCommandRepository.findByCommandIdForUpdate(commandId))
                .thenReturn(Optional.of(command));

        ackInboxService.handle(record);

        assertEquals(OrderReturnInventoryCommandStatus.FAILED, command.getStatus());
        assertEquals("BAD_REQUEST", command.getFailureCode());
        verify(orderReturnInventoryCommandRepository, never()).saveAndFlush(command);
        verify(orderReturnService, never()).recordPhysicalCompletion(any(), any(), any());
    }
}
