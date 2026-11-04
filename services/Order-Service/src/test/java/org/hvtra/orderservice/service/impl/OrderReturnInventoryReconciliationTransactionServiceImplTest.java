package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReturnInventoryReconciliationTransactionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock private OrderReturnInventoryCommandRepository commands;
    @Mock private OrderOutboxPublisher outbox;
    @Mock private OrderReturnInventoryCommandReconciliationWriter writer;
    @InjectMocks private OrderReturnInventoryReconciliationTransactionServiceImpl service;

    @BeforeEach
    void configureOrphanRecoveryPolicy() {
        // Constructor does not expose @Value settings; set only configuration, never invoke private behavior.
        ReflectionTestUtils.setField(service, "orphanRecoveryEnabled", true);
        ReflectionTestUtils.setField(service, "notFoundThreshold", 3);
        ReflectionTestUtils.setField(service, "minCommandAgeMinutes", 10);
        ReflectionTestUtils.setField(service, "cooldownMinutes", 10);
        ReflectionTestUtils.setField(service, "maxRecoveryAttempts", 5);
    }

    @Test
    void incompleteReconciliationInputsNeverTouchPersistentState() {
        service.applyReconciledStatus(null, ReturnDispositionStatusResponse.builder().build());
        service.applyReconciledStatus(UUID.randomUUID(), null);
        service.applyReconciledStatus(UUID.randomUUID(), ReturnDispositionStatusResponse.builder().build());
        service.handleNotFound(null);
        service.resetNotFoundCounter(null);
        verifyNoInteractions(commands, outbox, writer);
    }

    @ParameterizedTest
    @EnumSource(value = ReturnDispositionReconciliationStatus.class, names = {"PROCESSED", "FAILED", "PROCESSING"})
    void passesExactCommandInspectionAndFailureEvidenceToWriter(ReturnDispositionReconciliationStatus status) {
        UUID commandId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        var response = new ReturnDispositionStatusResponse(commandId, status, UUID.randomUUID(), inspectionId, "QC_FAILED", "failure", NOW);
        service.applyReconciledStatus(commandId, response);
        ArgumentCaptor<OrderReturnInventoryCommandReconciliationWriter.CommandEvidence> captured =
                ArgumentCaptor.forClass(OrderReturnInventoryCommandReconciliationWriter.CommandEvidence.class);
        verify(writer).reconcileCommand(captured.capture());
        var evidence = captured.getValue();
        assertEquals(commandId, evidence.commandId());
        assertEquals(inspectionId, evidence.inspectionId());
        assertEquals(status, evidence.status());
        assertEquals(NOW, evidence.processedAt());
        assertEquals(status == ReturnDispositionReconciliationStatus.PROCESSED, evidence.isPosted());
        assertEquals(status == ReturnDispositionReconciliationStatus.FAILED, evidence.isFailed());
        assertEquals("QC_FAILED", evidence.failureCode());
        verifyNoInteractions(commands, outbox);
    }

    @Test
    void missingCommandsDoNotCreateRecoveryEventsOrCounterRows() {
        UUID commandId = UUID.randomUUID();
        when(commands.findByCommandIdForUpdate(commandId)).thenReturn(Optional.empty());
        service.handleNotFound(commandId);
        service.resetNotFoundCounter(commandId);
        verify(commands, times(2)).findByCommandIdForUpdate(commandId);
        verifyNoMoreInteractions(commands);
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @EnumSource(value = OrderReturnInventoryCommandStatus.class, names = {"PROCESSED", "FAILED"})
    void terminalCommandsCannotBeRepublishedOrHaveCountersReset(OrderReturnInventoryCommandStatus status) {
        OrderReturnInventoryCommand command = command();
        command.setStatus(status);
        command.setNotFoundCount(4);
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        service.handleNotFound(command.getCommandId());
        service.resetNotFoundCounter(command.getCommandId());
        assertEquals(status, command.getStatus());
        assertEquals(4, command.getNotFoundCount());
        verify(commands, never()).saveAndFlush(command);
        verifyNoInteractions(outbox);
    }

    @ParameterizedTest
    @CsvSource({"601, 2, 0, true", "600, 2, 0, false", "599, 2, 0, false", "601, 1, 0, false",
            "601, 2, 599, false", "601, 2, 600, false", "601, 2, 601, true"})
    void recoveryRequiresThresholdStrictMinimumAgeAndCooldown(long ageSeconds, int misses, long cooldownSeconds, boolean recover) {
        OrderReturnInventoryCommand command = command();
        command.setCreatedAt(NOW.minusSeconds(ageSeconds));
        command.setNotFoundCount(misses);
        command.setLastRecoveryAt(cooldownSeconds == 0 ? null : NOW.minusSeconds(cooldownSeconds));
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        atFixedTime(() -> service.handleNotFound(command.getCommandId()));
        assertEquals(misses + 1, command.getNotFoundCount());
        assertEquals(NOW, command.getLastNotFoundAt());
        assertEquals(recover ? 1 : 0, command.getRecoveryAttemptCount());
        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        var sequence = inOrder(commands, outbox);
        sequence.verify(commands).findByCommandIdForUpdate(command.getCommandId());
        sequence.verify(commands).saveAndFlush(command);
        if (recover) {
            assertEquals(NOW, command.getLastRecoveryAt());
            sequence.verify(outbox).publishReturnInventoryDispositionRecovery(command, 1);
        } else {
            verifyNoInteractions(outbox);
        }
    }

    @Test
    void exhaustedRecoveryRetainsPendingReservationWithoutSendingAnotherCommand() {
        OrderReturnInventoryCommand command = command();
        command.setRecoveryAttemptCount(5);
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        atFixedTime(() -> service.handleNotFound(command.getCommandId()));
        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        assertEquals("ORPHAN_RECOVERY_EXHAUSTED", command.getFailureCode());
        assertNull(command.getFailedAt());
        assertEquals(NOW, command.getLastNotFoundAt());
        verify(commands).saveAndFlush(command);
        verifyNoInteractions(outbox);
    }

    @Test
    void reconciliationCannotApplyEvidenceForAnotherCommand() {
        service.applyReconciledStatus(UUID.randomUUID(), new ReturnDispositionStatusResponse(
                UUID.randomUUID(), ReturnDispositionReconciliationStatus.PROCESSED,
                UUID.randomUUID(), UUID.randomUUID(), null, null, NOW));
        verifyNoInteractions(commands, outbox, writer);
    }

    @Test
    void finalAllowedRecoveryRemainsPendingUntilInventoryEvidenceArrives() {
        OrderReturnInventoryCommand command = command();
        command.setCreatedAt(NOW.minusSeconds(601));
        command.setNotFoundCount(2);
        command.setRecoveryAttemptCount(4);
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        atFixedTime(() -> service.handleNotFound(command.getCommandId()));
        assertEquals(OrderReturnInventoryCommandStatus.PENDING, command.getStatus());
        assertEquals(5, command.getRecoveryAttemptCount());
        verify(outbox).publishReturnInventoryDispositionRecovery(command, 5);
    }

    @Test
    void disabledRecoveryStillRecordsNotFoundAndHandlesNullLegacyCounters() {
        ReflectionTestUtils.setField(service, "orphanRecoveryEnabled", false);
        OrderReturnInventoryCommand command = command();
        command.setRecoveryAttemptCount(null);
        command.setNotFoundCount(null);
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        atFixedTime(() -> service.handleNotFound(command.getCommandId()));
        assertEquals(1, command.getNotFoundCount());
        assertEquals(NOW, command.getLastNotFoundAt());
        verify(commands).saveAndFlush(command);
        verifyNoInteractions(outbox);
    }

    @Test
    void processingEvidenceResetsConsecutiveMissesButPreservesRecoveryAttempts() {
        OrderReturnInventoryCommand command = command();
        command.setNotFoundCount(3);
        command.setLastNotFoundAt(NOW);
        command.setRecoveryAttemptCount(2);
        when(commands.findByCommandIdForUpdate(command.getCommandId())).thenReturn(Optional.of(command));
        service.resetNotFoundCounter(command.getCommandId());
        service.resetNotFoundCounter(command.getCommandId());
        assertEquals(0, command.getNotFoundCount());
        assertNull(command.getLastNotFoundAt());
        assertEquals(2, command.getRecoveryAttemptCount());
        verify(commands).saveAndFlush(command);
        verifyNoInteractions(outbox);
    }

    private OrderReturnInventoryCommand command() {
        return OrderReturnInventoryCommand.builder().commandId(UUID.randomUUID())
                .status(OrderReturnInventoryCommandStatus.PENDING).build();
    }

    private void atFixedTime(Runnable action) {
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            action.run();
        }
    }
}
