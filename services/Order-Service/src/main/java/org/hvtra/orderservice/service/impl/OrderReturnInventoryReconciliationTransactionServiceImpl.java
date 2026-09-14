package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationTransactionService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnInventoryReconciliationTransactionServiceImpl
        implements OrderReturnInventoryReconciliationTransactionService {

    private final OrderReturnInventoryCommandRepository commandRepository;
    private final OrderReturnService orderReturnService;
    private final OrderOutboxPublisher orderOutboxPublisher;

    @Value("${order.return.inventory-reconciliation.orphan-recovery.enabled:true}")
    private boolean orphanRecoveryEnabled;

    @Value("${order.return.inventory-reconciliation.orphan-recovery.not-found-threshold:3}")
    private int notFoundThreshold;

    @Value("${order.return.inventory-reconciliation.orphan-recovery.min-command-age-minutes:10}")
    private int minCommandAgeMinutes;

    @Value("${order.return.inventory-reconciliation.orphan-recovery.cooldown-minutes:10}")
    private int cooldownMinutes;

    @Value("${order.return.inventory-reconciliation.orphan-recovery.max-recovery-attempts:5}")
    private int maxRecoveryAttempts;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyReconciledStatus(UUID commandId, ReturnDispositionStatusResponse statusResponse) {
        if (commandId == null || statusResponse == null || statusResponse.status() == null) {
            return;
        }

        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdForUpdate(commandId);
        if (cmdOpt.isEmpty()) {
            log.warn("Command not found during reconciliation lock acquisition: commandId={}", commandId);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;

        // Double-check status INSIDE pessimistic write lock
        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.info("Reconciliation skipped because command is no longer PENDING: commandId={}, returnId={}, localStatus={}, inventoryStatus={}",
                    commandId, returnId, command.getStatus(), statusResponse.status());
            return;
        }

        if (statusResponse.status() == ReturnDispositionReconciliationStatus.PROCESSED) {
            command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
            command.setProcessedAt(statusResponse.processedAt() != null ? statusResponse.processedAt() : Instant.now());
            if (statusResponse.inspectionId() != null) {
                command.setInspectionId(statusResponse.inspectionId());
            }
            command.setFailedAt(null);
            command.setFailureCode(null);
            command.setFailureReason(null);
            commandRepository.saveAndFlush(command);

            log.info("Reconciled command PENDING -> PROCESSED: commandId={}, returnId={}, inspectionId={}",
                    commandId, returnId, command.getInspectionId());

            if (returnId != null) {
                orderReturnService.checkAndAutoCompleteNoRefundReturn(returnId);
            }
        } else if (statusResponse.status() == ReturnDispositionReconciliationStatus.FAILED) {
            command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
            command.setFailedAt(statusResponse.processedAt() != null ? statusResponse.processedAt() : Instant.now());
            command.setFailureCode(statusResponse.failureCode() != null ? statusResponse.failureCode() : "RECONCILED_FAILED");
            command.setFailureReason(statusResponse.failureReason() != null ? statusResponse.failureReason() : "Failed in inventory");
            commandRepository.saveAndFlush(command);

            log.warn("Reconciled command PENDING -> FAILED: commandId={}, returnId={}, failureCode={}, reason={}",
                    commandId, returnId, command.getFailureCode(), command.getFailureReason());
        } else {
            log.debug("Non-terminal status ignored inside transaction service: commandId={}, returnId={}, status={}",
                    commandId, returnId, statusResponse.status());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotFound(UUID commandId) {
        if (commandId == null) {
            return;
        }

        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdForUpdate(commandId);
        if (cmdOpt.isEmpty()) {
            log.warn("Command not found during NOT_FOUND handling: commandId={}", commandId);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();
        UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;

        // Double-check status INSIDE pessimistic write lock
        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.info("NOT_FOUND handling skipped because command is no longer PENDING: commandId={}, returnId={}, localStatus={}",
                    commandId, returnId, command.getStatus());
            return;
        }

        int currentRecoveryAttempts = command.getRecoveryAttemptCount() == null ? 0 : command.getRecoveryAttemptCount();

        // If recovery attempts are already exhausted: do not increment counter, do not save to DB, do not spam error logs
        if (currentRecoveryAttempts >= maxRecoveryAttempts) {
            log.debug("Command has exhausted max recovery attempts ({}/{}): commandId={}, returnId={}. Skipping counter update and log spam.",
                    currentRecoveryAttempts, maxRecoveryAttempts, commandId, returnId);
            return;
        }

        Instant now = Instant.now();
        int currentNotFoundCount = (command.getNotFoundCount() == null ? 0 : command.getNotFoundCount()) + 1;
        command.setNotFoundCount(currentNotFoundCount);
        command.setLastNotFoundAt(now);

        // Check if recovery criteria are met
        boolean eligibleForRecovery = orphanRecoveryEnabled
                && command.getCreatedAt() != null
                && command.getCreatedAt().isBefore(now.minus(Duration.ofMinutes(minCommandAgeMinutes)))
                && currentNotFoundCount >= notFoundThreshold
                && currentRecoveryAttempts < maxRecoveryAttempts
                && (command.getLastRecoveryAt() == null || command.getLastRecoveryAt().isBefore(now.minus(Duration.ofMinutes(cooldownMinutes))));

        if (eligibleForRecovery) {
            int nextAttempt = currentRecoveryAttempts + 1;
            command.setRecoveryAttemptCount(nextAttempt);
            command.setLastRecoveryAt(now);
            commandRepository.saveAndFlush(command);

            orderOutboxPublisher.publishReturnInventoryDispositionRecovery(command, nextAttempt);

            if (nextAttempt >= maxRecoveryAttempts) {
                log.error("CRITICAL: Republished FINAL orphan recovery attempt ({}/{}) for command: commandId={}, returnId={}, notFoundCount={}. If this attempt fails, command will remain PENDING and require manual intervention!",
                        nextAttempt, maxRecoveryAttempts, commandId, returnId, currentNotFoundCount);
            } else {
                log.warn("Republished orphan recovery event for command: commandId={}, returnId={}, attempt={}/{}, notFoundCount={}",
                        commandId, returnId, nextAttempt, maxRecoveryAttempts, currentNotFoundCount);
            }
        } else {
            commandRepository.saveAndFlush(command);

            log.info("Recorded NOT_FOUND for command: commandId={}, returnId={}, notFoundCount={}/{}, recoveryAttempts={}/{}",
                    commandId, returnId, currentNotFoundCount, notFoundThreshold, currentRecoveryAttempts, maxRecoveryAttempts);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetNotFoundCounter(UUID commandId) {
        if (commandId == null) {
            return;
        }

        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdForUpdate(commandId);
        if (cmdOpt.isEmpty()) {
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();

        // Double-check status INSIDE pessimistic write lock
        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            return;
        }

        Integer currentCount = command.getNotFoundCount();
        if (currentCount != null && currentCount > 0) {
            command.setNotFoundCount(0);
            command.setLastNotFoundAt(null);
            commandRepository.saveAndFlush(command);
            log.info("Reset consecutive notFoundCount to 0 for command because Inventory is PROCESSING: commandId={}", commandId);
        }
    }
}
