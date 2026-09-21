package org.hvtra.orderservice.service.impl;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReturnInventoryReconciliationServiceImplTest {

    @Mock
    private OrderReturnInventoryCommandRepository commandRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderReturnInventoryReconciliationTransactionService transactionService;

    @InjectMocks
    private OrderReturnInventoryReconciliationServiceImpl reconciliationService;

    private UUID commandId;
    private UUID returnId;
    private OrderReturnInventoryCommand command;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciliationService, "pendingThresholdMinutes", 5);
        ReflectionTestUtils.setField(reconciliationService, "batchSize", 50);

        commandId = UUID.randomUUID();
        returnId = UUID.randomUUID();

        OrderReturn orderReturn = new OrderReturn();
        orderReturn.setId(returnId);

        command = new OrderReturnInventoryCommand();
        command.setId(UUID.randomUUID());
        command.setCommandId(commandId);
        command.setOrderReturn(orderReturn);
        command.setStatus(OrderReturnInventoryCommandStatus.PENDING);
    }

    @Test
    @DisplayName("Fast local check skips non-pending commands before calling Inventory")
    void testFastLocalCheckSkipsNonPendingCommands() {
        command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));

        reconciliationService.reconcileSingleCommand(commandId);

        verify(inventoryClient, never()).getReturnDispositionStatus(any());
        verify(transactionService, never()).applyReconciledStatus(any(), any());
    }

    @Test
    @DisplayName("Network failure / 503 fails gracefully without modifying local state")
    void testInventoryUnavailableFailsGracefully() {
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));
        Request request = Request.create(Request.HttpMethod.GET, "/api/inventory", Map.of(), null, new RequestTemplate());
        when(inventoryClient.getReturnDispositionStatus(commandId))
                .thenThrow(new FeignException.ServiceUnavailable("503 Unavailable", request, null, Map.of()));

        reconciliationService.reconcileSingleCommand(commandId);

        verify(transactionService, never()).applyReconciledStatus(any(), any());
        verify(transactionService, never()).handleNotFound(any());
        verify(transactionService, never()).resetNotFoundCounter(any());
    }

    @Test
    @DisplayName("PROCESSING from Inventory resets not-found counter and does NOT mark PROCESSED")
    void testInventoryProcessingLeavesPendingAndResetsCounter() {
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));
        ReturnDispositionStatusResponse response = ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.PROCESSING)
                .build();
        when(inventoryClient.getReturnDispositionStatus(commandId)).thenReturn(response);

        reconciliationService.reconcileSingleCommand(commandId);

        verify(transactionService).resetNotFoundCounter(commandId);
        verify(transactionService, never()).applyReconciledStatus(any(), any());
        verify(transactionService, never()).handleNotFound(any());
    }

    @Test
    @DisplayName("NOT_FOUND from Inventory delegates to orphan recovery without guessing PROCESSED")
    void testInventoryNotFoundDelegatesToHandleNotFound() {
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));
        ReturnDispositionStatusResponse response = ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.NOT_FOUND)
                .build();
        when(inventoryClient.getReturnDispositionStatus(commandId)).thenReturn(response);

        reconciliationService.reconcileSingleCommand(commandId);

        verify(transactionService).handleNotFound(commandId);
        verify(transactionService, never()).applyReconciledStatus(any(), any());
        verify(transactionService, never()).resetNotFoundCounter(any());
    }

    @Test
    @DisplayName("Terminal PROCESSED delegates to transaction service")
    void testInventoryProcessedDelegatesToApplyReconciledStatus() {
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));
        ReturnDispositionStatusResponse response = ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.PROCESSED)
                .inspectionId(UUID.randomUUID())
                .processedAt(Instant.now())
                .build();
        when(inventoryClient.getReturnDispositionStatus(commandId)).thenReturn(response);

        reconciliationService.reconcileSingleCommand(commandId);

        verify(transactionService).applyReconciledStatus(commandId, response);
    }

    @Test
    @DisplayName("Terminal FAILED delegates to transaction service")
    void testInventoryFailedDelegatesToApplyReconciledStatus() {
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(Optional.of(command));
        ReturnDispositionStatusResponse response = ReturnDispositionStatusResponse.builder()
                .commandId(commandId)
                .status(ReturnDispositionReconciliationStatus.FAILED)
                .failureCode("CORRUPT")
                .failureReason("Damaged")
                .build();
        when(inventoryClient.getReturnDispositionStatus(commandId)).thenReturn(response);

        reconciliationService.reconcileSingleCommand(commandId);

        verify(transactionService).applyReconciledStatus(commandId, response);
    }

    @Test
    @DisplayName("Scheduled reconciliation batches stale pending commands and continues on single error")
    void testReconcileStalePendingCommandsBatchesAndContinuesOnError() {
        UUID commandId2 = UUID.randomUUID();
        when(commandRepository.findStaleCommandIds(eq(OrderReturnInventoryCommandStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(commandId, commandId2));

        // command 1 throws exception
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId))
                .thenThrow(new RuntimeException("DB glitch"));

        // command 2 processes successfully
        OrderReturnInventoryCommand command2 = new OrderReturnInventoryCommand();
        command2.setId(UUID.randomUUID());
        command2.setCommandId(commandId2);
        command2.setStatus(OrderReturnInventoryCommandStatus.PENDING);
        when(commandRepository.findByCommandIdAndIsDeletedFalse(commandId2)).thenReturn(Optional.of(command2));
        ReturnDispositionStatusResponse response2 = ReturnDispositionStatusResponse.builder()
                .commandId(commandId2)
                .status(ReturnDispositionReconciliationStatus.PROCESSED)
                .build();
        when(inventoryClient.getReturnDispositionStatus(commandId2)).thenReturn(response2);

        reconciliationService.reconcileStalePendingCommands();

        // command 2 is still processed despite command 1 failure
        verify(transactionService).applyReconciledStatus(commandId2, response2);
    }
}
