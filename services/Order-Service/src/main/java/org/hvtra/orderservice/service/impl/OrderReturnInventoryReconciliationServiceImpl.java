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
import org.hvtra.orderservice.service.OrderReturnService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderReturnService orderReturnService;
    private final ObjectProvider<OrderReturnInventoryReconciliationService> selfProvider;

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

        OrderReturnInventoryReconciliationService self = selfProvider.getIfAvailable();
        for (UUID commandId : staleCommandIds) {
            try {
                if (self != null) {
                    self.reconcileSingleCommand(commandId);
                } else {
                    reconcileSingleCommand(commandId);
                }
            } catch (Exception ex) {
                log.error("Unexpected error reconciling commandId {}: {}", commandId, ex.getMessage(), ex);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSingleCommand(UUID commandId) {
        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdForUpdate(commandId);
        if (cmdOpt.isEmpty()) {
            log.warn("Command not found during reconciliation: {}", commandId);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.debug("Command {} is no longer PENDING (current status={}), skipping reconciliation.",
                    commandId, command.getStatus());
            return;
        }

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

        switch (statusResponse.status()) {
            case PROCESSED -> {
                command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
                command.setProcessedAt(statusResponse.processedAt() != null ? statusResponse.processedAt() : Instant.now());
                if (statusResponse.inspectionId() != null) {
                    command.setInspectionId(statusResponse.inspectionId());
                }
                command.setFailedAt(null);
                command.setFailureCode(null);
                command.setFailureReason(null);
                commandRepository.saveAndFlush(command);
                log.info("Reconciled command {} PENDING -> PROCESSED (inspectionId={})",
                        commandId, command.getInspectionId());

                if (command.getOrderReturn() != null && command.getOrderReturn().getId() != null) {
                    orderReturnService.checkAndAutoCompleteNoRefundReturn(command.getOrderReturn().getId());
                }
            }
            case FAILED -> {
                command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
                command.setFailedAt(statusResponse.processedAt() != null ? statusResponse.processedAt() : Instant.now());
                command.setFailureCode(statusResponse.failureCode() != null ? statusResponse.failureCode() : "RECONCILED_FAILED");
                command.setFailureReason(statusResponse.failureReason() != null ? statusResponse.failureReason() : "Failed in inventory");
                commandRepository.saveAndFlush(command);
                log.warn("Reconciled command {} PENDING -> FAILED (code={}, reason={})",
                        commandId, command.getFailureCode(), command.getFailureReason());
            }
            case PROCESSING -> {
                log.debug("Command {} is still PROCESSING in Inventory-Service. Leaving as PENDING.", commandId);
            }
            case NOT_FOUND -> {
                log.warn("Command {} NOT_FOUND in Inventory-Service inbox during reconciliation. " +
                        "Message may still be in transit or lag. Leaving as PENDING.", commandId);
            }
        }
    }
}
