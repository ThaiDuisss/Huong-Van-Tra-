package org.hvtra.iventoryservice.outbox.event;

public final class InventoryEventType {

    private InventoryEventType() {
    }

    public static final String RETURN_INVENTORY_DISPOSITION_PROCESSED =
            "RETURN_INVENTORY_DISPOSITION_PROCESSED";

    public static final String RETURN_INVENTORY_DISPOSITION_FAILED =
            "RETURN_INVENTORY_DISPOSITION_FAILED";

    /** Đóng trà theo yêu cầu: kho đã đóng gói và xuất nguyên liệu, Order cho phép giao hàng. */
    public static final String CUSTOM_BUNDLE_PACKED = "CUSTOM_BUNDLE_PACKED";
}
