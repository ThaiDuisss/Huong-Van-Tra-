package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnPhysicalCompletionHandler {

    private final OrderReturnRepository orderReturnRepository;
    private final OrderRepository orderRepository;
    private final OrderReturnPhysicalProgressResolver physicalProgressResolver;
    private final OrderReturnHistoryWriter orderReturnHistoryWriter;
    private final OrderOutboxPublisher orderOutboxPublisher;

    public boolean isPhysicalDispositionCompleted(UUID returnId) {
        if (returnId == null) {
            return false;
        }
        return physicalProgressResolver.resolve(returnId).physicalDone();
    }

    @Transactional
    public void recordPhysicalCompletion(UUID returnId, UUID actorUserId, UUID inspectionId) {
        if (returnId == null) {
            return;
        }
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId).orElse(null);
        if (orderReturn == null) {
            log.warn("Cannot record physical completion: return not found: {}", returnId);
            return;
        }

        String inspectionIdStr = inspectionId != null ? inspectionId.toString() : null;
        orderReturnHistoryWriter.writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.QC_COMPLETED,
                orderReturn.getStatus(),
                orderReturn.getStatus(),
                actorUserId,
                "Inventory physical disposition completed",
                "INVENTORY_INSPECTION",
                inspectionIdStr
        );
        log.info("Recorded QC_COMPLETED history for returnId={}, inspectionId={}", returnId, inspectionIdStr);
    }

    @Transactional
    public void checkAndAutoCompleteNoRefundReturn(UUID returnId) {
        if (returnId == null) {
            return;
        }

        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId).orElse(null);
        if (orderReturn == null) {
            log.warn("Cannot auto-complete no-refund return: return not found: {}", returnId);
            return;
        }

        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            log.debug("Skip no-refund auto-completion: returnId={} status is not RECEIVED (current: {})",
                    returnId, orderReturn.getStatus());
            return;
        }
        if (Boolean.TRUE.equals(orderReturn.getRefundRequired())) {
            log.debug("Skip no-refund auto-completion: returnId={} requires refund", returnId);
            return;
        }

        if (!isPhysicalDispositionCompleted(returnId)) {
            log.debug("Skip no-refund auto-completion: returnId={} physical disposition is not fully completed yet", returnId);
            return;
        }

        // For no-refund returns, physical disposition completion completes the return
        orderReturn.setStatus(ReturnStatus.COMPLETED);
        orderReturnRepository.save(orderReturn);

        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderReturn.getOrder().getId())
                .orElse(null);
        if (lockedOrder != null) {
            List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(lockedOrder.getId());
            lockedOrder.setReturnStatus(resolveOrderReturnStatus(lockedOrder, orderReturns));
            orderRepository.save(lockedOrder);
        }

        UUID actorUserId = currentActorUserIdOrNull();
        orderOutboxPublisher.publishReturnCompletedNotification(orderReturn, ReturnStatus.COMPLETED, actorUserId);
        log.info("Processed no-refund return completion: returnId={}, returnCode={}", orderReturn.getId(), orderReturn.getReturnCode());
    }

    private OrderReturnStatus resolveOrderReturnStatus(Order order, List<OrderReturn> orderReturns) {
        Map<UUID, BigDecimal> completedReturnedQuantities = orderReturns.stream()
                .filter(returnRecord -> ReturnStatus.RECEIVED == returnRecord.getStatus()
                        || ReturnStatus.COMPLETED == returnRecord.getStatus())
                .flatMap(returnRecord -> (returnRecord.getItems() == null
                        ? List.<OrderReturnItem>of()
                        : returnRecord.getItems()).stream())
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .filter(item -> item.getOrderItem() != null && item.getOrderItem().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getOrderItem().getId(),
                        item -> defaultZero(item.getQuantity()),
                        BigDecimal::add
                ));

        boolean anyReturned = completedReturnedQuantities.values().stream()
                .anyMatch(quantity -> quantity.compareTo(BigDecimal.ZERO) > 0);
        if (!anyReturned) {
            return OrderReturnStatus.NONE;
        }

        boolean fullyReturned = order.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .allMatch(item -> defaultZero(completedReturnedQuantities.get(item.getId()))
                        .compareTo(defaultZero(item.getQuantity())) >= 0);
        return fullyReturned ? OrderReturnStatus.FULLY_RETURNED : OrderReturnStatus.PARTIALLY_RETURNED;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private UUID currentActorUserIdOrNull() {
        try {
            String userId = SecurityUtils.currentUserId();
            return userId == null || userId.isBlank() ? null : UUID.fromString(userId);
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            return null;
        }
    }
}
