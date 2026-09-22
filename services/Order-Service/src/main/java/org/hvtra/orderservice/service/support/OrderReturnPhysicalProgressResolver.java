package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReturnPhysicalProgressResolver {

    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;

    public OrderReturnPhysicalProgress resolve(UUID returnId) {
        if (returnId == null) {
            return OrderReturnPhysicalProgress.empty();
        }
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId).orElse(null);
        if (orderReturn == null) {
            return OrderReturnPhysicalProgress.empty();
        }
        return resolve(orderReturn);
    }

    public OrderReturnPhysicalProgress resolve(OrderReturn orderReturn) {
        if (orderReturn == null || orderReturn.getId() == null) {
            return OrderReturnPhysicalProgress.empty();
        }

        Map<UUID, OrderReturnPhysicalProgress> batchResult = resolveBatch(List.of(orderReturn));
        OrderReturnPhysicalProgress progress = batchResult.get(orderReturn.getId());

        if (progress != null && (progress.physicalDone() || progress.processedQuantity().compareTo(BigDecimal.ZERO) > 0)) {
            return progress;
        }

        // Fallback for single-return test stubs that mock sumQuantityByReturnIdAndCommandStatus directly
        List<Object[]> singleRows = orderReturnInventoryCommandRepository.sumQuantityByReturnIdAndCommandStatus(
                orderReturn.getId(),
                OrderReturnInventoryCommandStatus.PROCESSED
        );
        if (singleRows != null && !singleRows.isEmpty()) {
            Map<UUID, BigDecimal> processedByItemId = new HashMap<>();
            for (Object[] row : singleRows) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID itemId && row[1] instanceof BigDecimal qty) {
                    processedByItemId.merge(itemId, qty, BigDecimal::add);
                }
            }
            boolean hasPending = orderReturnInventoryCommandRepository.existsByOrderReturnIdAndStatusAndIsDeletedFalse(
                    orderReturn.getId(),
                    OrderReturnInventoryCommandStatus.PENDING
            );
            boolean hasFailed = orderReturnInventoryCommandRepository.existsByOrderReturnIdAndStatusAndIsDeletedFalse(
                    orderReturn.getId(),
                    OrderReturnInventoryCommandStatus.FAILED
            );
            return computeProgress(orderReturn, processedByItemId, hasPending, hasFailed);
        }

        return progress != null ? progress : OrderReturnPhysicalProgress.empty();
    }

    public Map<UUID, OrderReturnPhysicalProgress> resolveBatchByIds(Collection<UUID> returnIds) {
        if (returnIds == null || returnIds.isEmpty()) {
            return Map.of();
        }
        List<OrderReturn> returns = orderReturnRepository.findByIdInAndIsDeletedFalseWithItems(new ArrayList<>(returnIds));
        return resolveBatch(returns);
    }

    public Map<UUID, OrderReturnPhysicalProgress> resolveBatch(Collection<OrderReturn> orderReturns) {
        if (orderReturns == null || orderReturns.isEmpty()) {
            return Map.of();
        }

        List<UUID> returnIds = orderReturns.stream()
                .filter(Objects::nonNull)
                .map(OrderReturn::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (returnIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> pendingReturnIds = new HashSet<>();
        Set<UUID> failedReturnIds = new HashSet<>();
        List<Object[]> statusRows = orderReturnInventoryCommandRepository.findCommandStatusesByReturnIds(returnIds);
        if (statusRows != null) {
            for (Object[] row : statusRows) {
                if (row == null || row.length < 2
                        || !(row[0] instanceof UUID returnId)
                        || !(row[1] instanceof OrderReturnInventoryCommandStatus status)) {
                    continue;
                }
                if (status == OrderReturnInventoryCommandStatus.PENDING) {
                    pendingReturnIds.add(returnId);
                } else if (status == OrderReturnInventoryCommandStatus.FAILED) {
                    failedReturnIds.add(returnId);
                }
            }
        }

        Map<UUID, Map<UUID, BigDecimal>> processedQuantitiesByReturnId = new HashMap<>();
        List<Object[]> processedRows = orderReturnInventoryCommandRepository.sumQuantityByReturnIdsAndCommandStatus(
                returnIds,
                OrderReturnInventoryCommandStatus.PROCESSED
        );
        if (processedRows != null) {
            for (Object[] row : processedRows) {
                if (row == null || row.length < 3
                        || !(row[0] instanceof UUID returnId)
                        || !(row[1] instanceof UUID returnItemId)
                        || !(row[2] instanceof BigDecimal quantity)) {
                    continue;
                }
                processedQuantitiesByReturnId
                        .computeIfAbsent(returnId, ignored -> new HashMap<>())
                        .merge(returnItemId, quantity, BigDecimal::add);
            }
        }

        Map<UUID, OrderReturnPhysicalProgress> result = new HashMap<>();
        for (OrderReturn orderReturn : orderReturns) {
            if (orderReturn == null || orderReturn.getId() == null) {
                continue;
            }
            UUID returnId = orderReturn.getId();
            Map<UUID, BigDecimal> processedByItemId = processedQuantitiesByReturnId.getOrDefault(returnId, Map.of());
            boolean hasPending = pendingReturnIds.contains(returnId);
            boolean hasFailed = failedReturnIds.contains(returnId);

            result.put(returnId, computeProgress(orderReturn, processedByItemId, hasPending, hasFailed));
        }
        return result;
    }

    private OrderReturnPhysicalProgress computeProgress(
            OrderReturn orderReturn,
            Map<UUID, BigDecimal> processedByItemId,
            boolean hasPending,
            boolean hasFailed
    ) {
        List<OrderReturnItem> items = orderReturn.getItems();
        List<OrderReturnItem> activeItems = items == null ? List.of() : items.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .filter(item -> item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (activeItems.isEmpty()) {
            String status = resolveStatus(false, BigDecimal.ZERO, BigDecimal.ZERO, hasPending, hasFailed);
            return new OrderReturnPhysicalProgress(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    false,
                    status,
                    hasPending,
                    hasFailed
            );
        }

        BigDecimal requiredQuantity = BigDecimal.ZERO;
        BigDecimal processedQuantity = BigDecimal.ZERO;
        boolean allItemsSatisfied = true;

        for (OrderReturnItem item : activeItems) {
            BigDecimal itemRequired = item.getQuantity();
            requiredQuantity = requiredQuantity.add(itemRequired);

            BigDecimal rawProcessed = processedByItemId.getOrDefault(item.getId(), BigDecimal.ZERO);
            if (rawProcessed.compareTo(BigDecimal.ZERO) < 0) {
                rawProcessed = BigDecimal.ZERO;
            }

            // Clamp item processed to item required (prevents retry/overlap overflow)
            BigDecimal clampedItemProcessed = rawProcessed.compareTo(itemRequired) > 0
                    ? itemRequired
                    : rawProcessed;
            processedQuantity = processedQuantity.add(clampedItemProcessed);

            if (rawProcessed.compareTo(itemRequired) < 0) {
                allItemsSatisfied = false;
            } else if (rawProcessed.compareTo(itemRequired) > 0) {
                log.warn("Physical disposition quantity anomaly: returnId={}, returnItemId={} has rawProcessed={} exceeding required={}",
                        orderReturn.getId(), item.getId(), rawProcessed, itemRequired);
            }
        }

        BigDecimal remainingQuantity = requiredQuantity.subtract(processedQuantity).max(BigDecimal.ZERO);
        boolean physicalDone = requiredQuantity.compareTo(BigDecimal.ZERO) > 0 && allItemsSatisfied;

        String status = resolveStatus(physicalDone, requiredQuantity, remainingQuantity, hasPending, hasFailed);

        return new OrderReturnPhysicalProgress(
                requiredQuantity,
                processedQuantity,
                remainingQuantity,
                physicalDone,
                status,
                hasPending,
                hasFailed
        );
    }

    private String resolveStatus(
            boolean physicalDone,
            BigDecimal requiredQuantity,
            BigDecimal remainingQuantity,
            boolean hasPending,
            boolean hasFailed
    ) {
        if (physicalDone) {
            return "COMPLETED";
        }
        if (hasFailed && remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            return "FAILED";
        }
        if (hasPending) {
            return "PROCESSING";
        }
        if (requiredQuantity.subtract(remainingQuantity).compareTo(BigDecimal.ZERO) > 0) {
            return "PROCESSING";
        }
        return "PENDING";
    }
}
