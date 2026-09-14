package org.hvtra.orderservice.service;

import java.util.UUID;

public interface OrderReturnInventoryReconciliationService {

    void reconcileStalePendingCommands();

    void reconcileSingleCommand(UUID commandId);
}
