package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnInventoryCommandReconciliationWriter {

    private final OrderReturnInventoryCommandRepository commandRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileCompletedReturns(Map<UUID, CompletionEvidence> evidenceByReturnId) {
        if (evidenceByReturnId == null || evidenceByReturnId.isEmpty()) {
            return;
        }

        Set<UUID> returnIds = evidenceByReturnId.keySet().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (returnIds.isEmpty()) {
            return;
        }

        Collection<OrderReturnInventoryCommand> pendingCommands =
                commandRepository.findByOrderReturnIdsAndStatusForUpdate(
                        returnIds,
                        OrderReturnInventoryCommandStatus.PENDING
                );
        if (pendingCommands.isEmpty()) {
            return;
        }

        Instant reconciledAt = Instant.now();
        for (OrderReturnInventoryCommand command : pendingCommands) {
            UUID returnId = command.getOrderReturn().getId();
            CompletionEvidence evidence = evidenceByReturnId.get(returnId);
            if (evidence == null) {
                continue;
            }

            command.setStatus(OrderReturnInventoryCommandStatus.PROCESSED);
            command.setProcessedAt(evidence.processedAt() != null ? evidence.processedAt() : reconciledAt);
            if (evidence.inspectionId() != null) {
                command.setInspectionId(evidence.inspectionId());
            }
            command.setFailedAt(null);
            command.setFailureCode(null);
            command.setFailureReason(null);
        }

        commandRepository.saveAllAndFlush(pendingCommands);
        log.info(
                "Reconciled {} pending return inventory commands to PROCESSED from terminal Inventory QC summaries",
                pendingCommands.size()
        );
    }

    public record CompletionEvidence(UUID inspectionId, Instant processedAt) {
    }
}
