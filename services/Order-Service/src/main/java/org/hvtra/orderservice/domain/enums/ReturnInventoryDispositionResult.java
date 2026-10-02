package org.hvtra.orderservice.domain.enums;

public enum ReturnInventoryDispositionResult {
    RESTOCK,
    // Retained for persisted command/history reads; not allowed in new return requests.
    QUARANTINE,
    DAMAGED,
    WRITE_OFF;

    public boolean isAllowedForNewReturn() {
        return this == RESTOCK || this == DAMAGED || this == WRITE_OFF;
    }
}
