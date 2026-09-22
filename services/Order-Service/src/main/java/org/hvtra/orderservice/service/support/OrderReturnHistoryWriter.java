package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnHistoryWriter {

    private final OrderReturnHistoryRepository orderReturnHistoryRepository;

    public void writeIfAbsent(
            OrderReturn orderReturn,
            OrderReturnHistoryAction action,
            ReturnStatus fromStatus,
            ReturnStatus toStatus,
            UUID actorUserId,
            String note,
            String referenceType,
            String referenceId
    ) {
        if (orderReturn == null || orderReturn.getId() == null || action == null) {
            return;
        }
        if (orderReturnHistoryRepository.existsByOrderReturnIdAndActionAndIsDeletedFalse(
                orderReturn.getId(),
                action
        )) {
            return;
        }

        OrderReturnHistory history = OrderReturnHistory.builder()
                .orderReturn(orderReturn)
                .action(action)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorUserId(actorUserId)
                .note(note)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .occurredAt(Instant.now())
                .build();

        try {
            orderReturnHistoryRepository.save(history);
        } catch (DataIntegrityViolationException ex) {
            if (!orderReturnHistoryRepository.existsByOrderReturnIdAndActionAndIsDeletedFalse(
                    orderReturn.getId(),
                    action
            )) {
                throw ex;
            }
            log.debug("Ignore duplicate return history. returnId={}, action={}", orderReturn.getId(), action);
        }
    }
}
