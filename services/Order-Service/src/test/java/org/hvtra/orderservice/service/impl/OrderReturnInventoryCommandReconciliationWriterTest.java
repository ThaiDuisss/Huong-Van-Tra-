package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnInventoryCommandReconciliationWriterTest {

    @Mock
    private OrderReturnInventoryCommandRepository commandRepository;

    @Mock
    private OrderReturnPhysicalCompletionHandler physicalCompletionHandler;

    @InjectMocks
    private OrderReturnInventoryCommandReconciliationWriter writer;

    private UUID returnId;
    private UUID commandId;
    private UUID inspectionId;
    private UUID requestedBy;
    private OrderReturn orderReturn;
    private OrderReturnInventoryCommand command;

    @BeforeEach
    void setUp() {
        returnId = UUID.randomUUID();
        commandId = UUID.randomUUID();
        inspectionId = UUID.randomUUID();
        requestedBy = UUID.randomUUID();

        orderReturn = new OrderReturn();
        orderReturn.setId(returnId);

        command = new OrderReturnInventoryCommand();
        command.setId(UUID.randomUUID());
        command.setCommandId(commandId);
        command.setOrderReturn(orderReturn);
        command.setStatus(OrderReturnInventoryCommandStatus.PENDING);
        command.setRequestedBy(requestedBy);
    }

    @Test
    @DisplayName("Case 1: PENDING command with Inventory POSTED transitions to PROCESSED")
    void testPendingCommandWithPostedEvidenceTransitionsToProcessed() {
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));
        when(physicalCompletionHandler.isPhysicalDispositionCompleted(returnId))
                .thenReturn(false) // beforePhysicalDone
                .thenReturn(true);  // afterPhysicalDone

        Instant processedAt = Instant.now();
        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        processedAt,
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        assertEquals(processedAt, command.getProcessedAt());
        assertEquals(inspectionId, command.getInspectionId());
        assertNull(command.getFailureCode());
        assertNull(command.getFailureReason());
        verify(commandRepository).saveAndFlush(command);
        verify(physicalCompletionHandler).recordPhysicalCompletion(returnId, requestedBy, inspectionId);
        verify(physicalCompletionHandler).checkAndAutoCompleteNoRefundReturn(returnId);
    }

    @Test
    @DisplayName("Case 2 & 3: PROCESSING or CREATED evidence leaves command as PENDING")
    void testProcessingOrCreatedEvidenceLeavesCommandPending() {
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSING,
                        "PROCESSING",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        verify(commandRepository, never()).saveAndFlush(any());
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
        verify(physicalCompletionHandler, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    @DisplayName("Case 4: Multiple commands: only POSTED command is reconciled, partial progress does not record QC_COMPLETED")
    void testMultipleCommandsPartialProcessing() {
        UUID commandId2 = UUID.randomUUID();
        OrderReturnInventoryCommand command2 = new OrderReturnInventoryCommand();
        command2.setId(UUID.randomUUID());
        command2.setCommandId(commandId2);
        command2.setOrderReturn(orderReturn);
        command2.setStatus(OrderReturnInventoryCommandStatus.PENDING);
        command2.setRequestedBy(requestedBy);

        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));
        when(commandRepository.findByCommandIdForUpdate(commandId2)).thenReturn(Optional.of(command2));

        // When command1 is reconciled, physical progress is not done yet
        when(physicalCompletionHandler.isPhysicalDispositionCompleted(returnId)).thenReturn(false);

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence1 =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence2 =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId2,
                        ReturnDispositionReconciliationStatus.PROCESSING,
                        "PROCESSING",
                        null,
                        null,
                        null,
                        null
                );

        writer.reconcileCommands(List.of(evidence1, evidence2));

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command2.getStatus());
        verify(commandRepository).saveAndFlush(command);
        verify(commandRepository, never()).saveAndFlush(command2);
        // Because physicalDone is still false, completion is NOT recorded
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
        verify(physicalCompletionHandler, never()).checkAndAutoCompleteNoRefundReturn(any());
    }

    @Test
    @DisplayName("Case 5: Duplicate reconciliation on already PROCESSED command is idempotent")
    void testDuplicateReconciliationIsIdempotent() {
        command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
        command.setInspectionId(inspectionId);
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        verify(commandRepository, never()).saveAndFlush(any());
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    @DisplayName("Case 6: PROCESSED command does not downgrade when non-terminal or failed evidence arrives")
    void testProcessedCommandNeverDowngrades() {
        command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence failedEvidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.FAILED,
                        null,
                        null,
                        Instant.now(),
                        "SOME_ERROR",
                        "Failed"
                );

        writer.reconcileCommand(failedEvidence);

        assertEquals(OrderReturnInventoryCommandStatus.PROCESSED, command.getStatus());
        verify(commandRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Case 7: Unknown commandId skips without fallback to returnId")
    void testUnknownCommandIdSkipsGracefully() {
        UUID unknownCommandId = UUID.randomUUID();
        when(commandRepository.findByCommandIdForUpdate(unknownCommandId)).thenReturn(Optional.empty());

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        unknownCommandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        verify(commandRepository, never()).saveAndFlush(any());
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    @DisplayName("Case 8 & 9: false -> true physicalDone transition triggers QC_COMPLETED once and checks auto-complete")
    void testPhysicalDoneTransitionTriggersQcCompletedAndAutoComplete() {
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));
        when(physicalCompletionHandler.isPhysicalDispositionCompleted(returnId))
                .thenReturn(false) // before
                .thenReturn(true);  // after

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        verify(physicalCompletionHandler, times(1)).recordPhysicalCompletion(returnId, requestedBy, inspectionId);
        verify(physicalCompletionHandler, times(1)).checkAndAutoCompleteNoRefundReturn(returnId);
    }

    @Test
    @DisplayName("Case 10: Failed evidence transitions PENDING command to FAILED")
    void testFailedEvidenceTransitionsToFailed() {
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));
        Instant failedAt = Instant.now();

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.FAILED,
                        null,
                        null,
                        failedAt,
                        "DAMAGE_EXCEEDED",
                        "Item broken beyond repair"
                );

        writer.reconcileCommand(evidence);

        assertEquals(OrderReturnInventoryCommandStatus.FAILED, command.getStatus());
        assertEquals("DAMAGE_EXCEEDED", command.getFailureCode());
        assertEquals("Item broken beyond repair", command.getFailureReason());
        assertEquals(failedAt, command.getFailedAt());
        verify(commandRepository).saveAndFlush(command);
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
    }

    @Test
    @DisplayName("Case 11: Locally FAILED command is not silently overwritten to PROCESSED")
    void testLocallyFailedCommandDoesNotOverwrite() {
        command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
        command.setFailureCode("LOCAL_FAILURE");
        when(commandRepository.findByCommandIdForUpdate(commandId)).thenReturn(Optional.of(command));

        OrderReturnInventoryCommandReconciliationWriter.CommandEvidence evidence =
                new OrderReturnInventoryCommandReconciliationWriter.CommandEvidence(
                        commandId,
                        ReturnDispositionReconciliationStatus.PROCESSED,
                        "POSTED",
                        inspectionId,
                        Instant.now(),
                        null,
                        null
                );

        writer.reconcileCommand(evidence);

        assertEquals(OrderReturnInventoryCommandStatus.FAILED, command.getStatus());
        verify(commandRepository, never()).saveAndFlush(any());
        verify(physicalCompletionHandler, never()).recordPhysicalCompletion(any(), any(), any());
    }
}
