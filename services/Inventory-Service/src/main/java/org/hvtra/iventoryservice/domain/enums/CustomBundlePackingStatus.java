package org.hvtra.iventoryservice.domain.enums;

/** Trạng thái yêu cầu đóng trà theo yêu cầu (Custom Tea Bundle). */
public enum CustomBundlePackingStatus {
    /** Thiếu nguyên liệu: đơn chờ hàng, chưa giữ nguyên liệu. */
    WAITING_MATERIAL,
    /** Đã giữ đủ nguyên liệu, chờ nhân viên kho đóng gói. */
    AWAITING_PACKING,
    /** Đã đóng gói và xuất kho nguyên liệu. */
    PACKED,
    CANCELLED
}
