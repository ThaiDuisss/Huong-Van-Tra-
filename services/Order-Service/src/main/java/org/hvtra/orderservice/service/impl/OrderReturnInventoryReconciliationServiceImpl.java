package org.hvtra.orderservice.service.impl;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationService;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationTransactionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnInventoryReconciliationServiceImpl implements OrderReturnInventoryReconciliationService {

    private final OrderReturnInventoryCommandRepository commandRepository;
    private final InventoryClient inventoryClient;
    private final OrderReturnInventoryReconciliationTransactionService transactionService;

    @Value("${order.return.inventory-reconciliation.pending-threshold-minutes:5}")
    private int pendingThresholdMinutes;

    @Value("${order.return.inventory-reconciliation.batch-size:50}")
    private int batchSize;

    @Override
    public void reconcileStalePendingCommands() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(pendingThresholdMinutes));
        Pageable pageable = PageRequest.of(0, batchSize);
        List<UUID> staleCommandIds = commandRepository.findStaleCommandIds(
                OrderReturnInventoryCommandStatus.PENDING,
                threshold,
                pageable
        );

        if (staleCommandIds.isEmpty()) {
            log.debug("No stale pending return inventory commands found for reconciliation.");
            return;
        }

        log.info("Found {} stale pending return inventory commands older than {} minutes to reconcile.",
                staleCommandIds.size(), pendingThresholdMinutes);

        for (UUID commandId : staleCommandIds) {
            try {
                reconcileSingleCommand(commandId);
            } catch (Exception ex) {
                log.error("Unexpected error reconciling commandId {}: {}", commandId, ex.getMessage(), ex);
            }
        }
    }

    @Override
    public void reconcileSingleCommand(UUID commandId) {
        if (commandId == null) {
            return;
        }

        // STEP 1: Fast local check without pessimistic lock or write transaction
        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdAndIsDeletedFalse(commandId);
        if (cmdOpt.isEmpty()) {
            log.debug("Command not found during fast local check: commandId={}", commandId);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.debug("Command is no longer PENDING in fast local check (current status={}), skipping reconciliation: commandId={}",
                    command.getStatus(), commandId);
            return;
        }

        // STEP 2: Network I/O outside DB transaction and outside pessimistic lock
        ReturnDispositionStatusResponse statusResponse;
        try {
            statusResponse = inventoryClient.getReturnDispositionStatus(commandId);
        } catch (FeignException ex) {
            log.warn("Failed to query disposition status from Inventory-Service for commandId {}: {}. Will retry next cycle.",
                    commandId, ex.getMessage());
            return;
        } catch (Exception ex) {
            log.error("Unexpected error querying Inventory-Service for commandId {}: {}. Will retry next cycle.",
                    commandId, ex.getMessage(), ex);
            return;
        }

        if (statusResponse == null || statusResponse.status() == null) {
            log.warn("Null status response from Inventory-Service for commandId {}. Will retry next cycle.", commandId);
            return;
        }

        if (statusResponse.status() == ReturnDispositionReconciliationStatus.PROCESSING) {
            UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;
            if (reconcileFromCompletedQc(commandId, returnId)) {
                return;
            }
            log.debug("Command {} is still PROCESSING in Inventory-Service. Leaving as PENDING and resetting consecutive NOT_FOUND counter.", commandId);
            transactionService.resetNotFoundCounter(commandId);
            return;
        }

        if (statusResponse.status() == ReturnDispositionReconciliationStatus.NOT_FOUND) {
            // Fallback check: Did Inventory already complete QC for this return?
            UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;
            if (reconcileFromCompletedQc(commandId, returnId)) {
                return;
            }

            transactionService.handleNotFound(commandId);
            return;
        }

        // STEP 3: Terminal status (PROCESSED or FAILED) -> delegate to short transaction service
        transactionService.applyReconciledStatus(commandId, statusResponse);
    }

    private boolean reconcileFromCompletedQc(UUID commandId, UUID returnId) {
        if (returnId == null) {
            return false;
        }
        try {
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                    inventoryClient.getCustomerReturnQcSummary(returnId);
            if (!isInventoryQcComplete(qcSummary)) {
                return false;
            }
            log.info(
                    "Command {} is not terminal in inventory inbox, but return {} is fully completed in Inventory QC. Reconciling to PROCESSED.",
                    commandId,
                    returnId
            );
            transactionService.applyReconciledStatus(commandId, ReturnDispositionStatusResponse.builder()
                    .commandId(commandId)
                    .status(ReturnDispositionReconciliationStatus.PROCESSED)
                    .returnId(returnId)
                    .inspectionId(qcSummary.inspectionId())
                    .processedAt(qcSummary.inspectedAt() != null ? qcSummary.inspectedAt() : Instant.now())
                    .build());
            return true;
        } catch (Exception ex) {
            log.debug("Fallback QC summary check for returnId {} failed: {}", returnId, ex.getMessage());
            return false;
        }
    }

    private boolean isInventoryQcComplete(InventoryClient.CustomerReturnQcSummaryResponse qcSummary) {
        if (qcSummary == null || qcSummary.lines() == null || qcSummary.lines().isEmpty()) {
            return false;
        }
        boolean hasTerminalStatus = "COMPLETED".equals(qcSummary.qcStatus())
                || "POSTED".equals(qcSummary.inspectionStatus());
        if (!hasTerminalStatus) {
            return false;
        }
        boolean anyPosted = qcSummary.lines().stream()
                .anyMatch(line -> line.postedQcQuantity() != null
                        && line.postedQcQuantity().compareTo(BigDecimal.ZERO) > 0);
        return anyPosted && qcSummary.lines().stream()
                .allMatch(line -> line.remainingQcQuantity() != null
                        && line.remainingQcQuantity().compareTo(BigDecimal.ZERO) == 0
                        && line.postedQcQuantity() != null
                        && line.returnedQuantity() != null
                        && line.postedQcQuantity().compareTo(line.returnedQuantity()) >= 0);
    }
}
