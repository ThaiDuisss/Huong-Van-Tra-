package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationTransactionService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}
