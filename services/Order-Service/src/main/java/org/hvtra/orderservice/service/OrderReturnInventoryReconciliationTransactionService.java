package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;

import java.util.UUID;

public interface OrderReturnInventoryReconciliationTransactionService {

    void applyReconciledStatus(UUID commandId, ReturnDispositionStatusResponse statusResponse);
}
