package org.hvtra.iventoryservice.domain.enums;

public enum CustomerReturnQcResult {
    RESTOCK,
    // Retained for historical inspection reads and completion of existing inspections.
    QUARANTINE,
    DAMAGED,
    WRITE_OFF;

    public boolean isAllowedForNewReturn() {
        return this == RESTOCK || this == DAMAGED || this == WRITE_OFF;
    }
}
