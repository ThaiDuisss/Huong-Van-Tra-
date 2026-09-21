package org.hvtra.orderservice.service.support;

import java.math.BigDecimal;

public record OrderReturnPhysicalProgress(
        BigDecimal requiredQuantity,
        BigDecimal processedQuantity,
        BigDecimal remainingQuantity,
        boolean physicalDone,
        String inventoryProcessingStatus,
        boolean hasPendingCommand,
        boolean hasFailedCommand
) {

    public OrderReturnPhysicalProgress(
            BigDecimal requiredQuantity,
            BigDecimal processedQuantity,
            BigDecimal remainingQuantity,
            boolean physicalDone,
            String inventoryProcessingStatus
    ) {
        this(requiredQuantity, processedQuantity, remainingQuantity, physicalDone, inventoryProcessingStatus, false, false);
    }

    public static OrderReturnPhysicalProgress empty() {
        return new OrderReturnPhysicalProgress(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                "PENDING",
                false,
                false
        );
    }
}
