package org.hvtra.orderservice.domain.enums;

public enum OrderInventoryProcessingStatus {
    NOT_STARTED,
    PROCESSING,
    RESERVED,
    CONSUMED,
    FAILED,
    RELEASED,
    /** Đơn chờ hàng: đã thu tiền nhưng chưa trừ tồn vì chưa có hàng giao. */
    WAITING_STOCK
}
