package org.hvtra.iventoryservice.outbox.event;

public final class InventoryEventType {

    private InventoryEventType() {
    }

    public static final String RETURN_INVENTORY_DISPOSITION_PROCESSED =
            "RETURN_INVENTORY_DISPOSITION_PROCESSED";

    public static final String RETURN_INVENTORY_DISPOSITION_FAILED =
            "RETURN_INVENTORY_DISPOSITION_FAILED";
}
