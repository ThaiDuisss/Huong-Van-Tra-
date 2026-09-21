package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnInventoryCommandReconciliationWriter {

    private final OrderReturnInventoryCommandRepository commandRepository;
    private final OrderReturnPhysicalCompletionHandler physicalCompletionHandler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileCommands(Collection<CommandEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return;
        }

        // Deduplicate evidences by commandId, keeping last entry
        Map<UUID, CommandEvidence> uniqueEvidences = evidences.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.commandId() != null)
                .collect(Collectors.toMap(
                        CommandEvidence::commandId,
                        Function.identity(),
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));

        if (uniqueEvidences.isEmpty()) {
            return;
        }

        // Deterministic ordering to prevent database deadlocks on concurrent batch updates
        List<CommandEvidence> sortedEvidences = new ArrayList<>(uniqueEvidences.values());
        sortedEvidences.sort(Comparator.comparing(CommandEvidence::commandId));

        for (CommandEvidence evidence : sortedEvidences) {
            reconcileSingleEvidence(evidence);
        }
    }

    public void reconcileCommand(CommandEvidence evidence) {
        if (evidence == null || evidence.commandId() == null) {
            return;
        }
        reconcileCommands(List.of(evidence));
    }

    private void reconcileSingleEvidence(CommandEvidence evidence) {
        UUID commandId = evidence.commandId();
        Optional<OrderReturnInventoryCommand> cmdOpt = commandRepository.findByCommandIdForUpdate(commandId);
        if (cmdOpt.isEmpty()) {
            log.warn("Reconciliation command missing locally: commandId={}", commandId);
            return;
        }

        OrderReturnInventoryCommand command = cmdOpt.get();

        if (command.getStatus() == OrderReturnInventoryCommandStatus.PROCESSED) {
            log.debug("Reconciliation idempotent skip: command already PROCESSED. commandId={}", commandId);
            return;
        }

        if (command.getStatus() == OrderReturnInventoryCommandStatus.FAILED) {
            if (evidence.isPosted()) {
                log.warn("Reconciliation conflict: commandId={} is FAILED locally but Inventory evidence shows POSTED (inspectionId={}). Manual investigation required.",
                        commandId, evidence.inspectionId());
            } else {
                log.debug("Command already FAILED, no recovery required: commandId={}", commandId);
            }
            return;
        }

        if (command.getStatus() != OrderReturnInventoryCommandStatus.PENDING) {
            log.debug("Command not in PENDING status (current: {}): commandId={}", command.getStatus(), commandId);
            return;
        }

        if (evidence.isPosted()) {
            UUID returnId = command.getOrderReturn() != null ? command.getOrderReturn().getId() : null;
            boolean beforePhysicalDone = returnId != null && physicalCompletionHandler.isPhysicalDispositionCompleted(returnId);

            command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
            command.setProcessedAt(evidence.processedAt() != null ? evidence.processedAt() : Instant.now());
            if (evidence.inspectionId() != null) {
                command.setInspectionId(evidence.inspectionId());
            }
            command.setFailedAt(null);
            command.setFailureCode(null);
            command.setFailureReason(null);
            commandRepository.saveAndFlush(command);

            log.info("Reconciled command PENDING -> PROCESSED: commandId={}, returnId={}, inspectionId={}",
                    commandId, returnId, command.getInspectionId());

            if (returnId != null) {
                boolean afterPhysicalDone = physicalCompletionHandler.isPhysicalDispositionCompleted(returnId);
                if (!beforePhysicalDone && afterPhysicalDone) {
                    physicalCompletionHandler.recordPhysicalCompletion(returnId, command.getRequestedBy(), command.getInspectionId());
                    physicalCompletionHandler.checkAndAutoCompleteNoRefundReturn(returnId);
                }
            }
        } else if (evidence.isFailed()) {
            command.setStatus(OrderReturnInventoryCommandStatus.FAILED);
            command.setFailedAt(evidence.processedAt() != null ? evidence.processedAt() : Instant.now());
            command.setFailureCode(evidence.failureCode() != null ? evidence.failureCode() : "RECONCILED_FAILED");
            command.setFailureReason(evidence.failureReason() != null ? evidence.failureReason() : "Failed in inventory");
            commandRepository.saveAndFlush(command);

            log.warn("Reconciled command PENDING -> FAILED: commandId={}, returnId={}, failureCode={}, reason={}",
                    commandId, command.getOrderReturn() != null ? command.getOrderReturn().getId() : null,
                    command.getFailureCode(), command.getFailureReason());
        } else {
            log.debug("Evidence status is not terminal (current: {}). Leaving command as PENDING. commandId={}",
                    evidence.status(), commandId);
        }
    }

    public record CommandEvidence(
            UUID commandId,
            ReturnDispositionReconciliationStatus status,
            String inspectionStatus,
            UUID inspectionId,
            Instant processedAt,
            String failureCode,
            String failureReason
    ) {
        public boolean isPosted() {
            return status == ReturnDispositionReconciliationStatus.PROCESSED
                    || "POSTED".equalsIgnoreCase(inspectionStatus);
        }

        public boolean isFailed() {
            return status == ReturnDispositionReconciliationStatus.FAILED;
        }

        public static CommandEvidence from(ReturnDispositionStatusResponse response) {
            if (response == null) {
                return null;
            }
            return new CommandEvidence(
                    response.commandId(),
                    response.status(),
                    response.status() == ReturnDispositionReconciliationStatus.PROCESSED ? "POSTED" : null,
                    response.inspectionId(),
                    response.processedAt(),
                    response.failureCode(),
                    response.failureReason()
            );
        }
    }

    @Deprecated
    public record CompletionEvidence(UUID inspectionId, Instant processedAt) {
    }

    @Deprecated
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileCompletedReturns(Map<UUID, CompletionEvidence> evidenceByReturnId) {
        log.warn("Deprecated reconcileCompletedReturns invoked with {} entries. Ignored to prevent bulk-updating multiple commands.",
                evidenceByReturnId != null ? evidenceByReturnId.size() : 0);
    }
}
