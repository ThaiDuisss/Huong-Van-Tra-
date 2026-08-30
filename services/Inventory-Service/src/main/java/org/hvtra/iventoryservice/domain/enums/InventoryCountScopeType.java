package org.hvtra.iventoryservice.domain.enums;

public enum InventoryCountScopeType {
    WAREHOUSE,
    ITEM,
    BATCH,
    MIXED,
    /** @deprecated transitional value; use WAREHOUSE with FULL_COUNT. */
    FULL,
    LOCATION,
    /** @deprecated transitional value; use ITEM with CYCLE_COUNT/SPOT_COUNT. */
    PARTIAL
}
