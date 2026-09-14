package org.hvtra.orderservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.service.OrderReturnInventoryReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "order.return.inventory-reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderReturnInventoryReconciliationScheduler {

    private final OrderReturnInventoryReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${order.return.inventory-reconciliation.fixed-delay-ms:60000}")
    public void runReconciliation() {
        try {
            reconciliationService.reconcileStalePendingCommands();
        } catch (Exception ex) {
            log.error("Unhandled error in return inventory reconciliation scheduler: {}", ex.getMessage(), ex);
        }
    }
}
