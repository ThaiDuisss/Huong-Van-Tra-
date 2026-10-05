package org.hvtra.catalog.enums;

public enum PriceChangeType {
    /** Mặt hàng được thêm vào bảng giá (giá cũ = null). */
    CREATED,
    /** Giá của Mặt hàng trong bảng giá thay đổi. */
    UPDATED,
    /** Mặt hàng bị gỡ khỏi bảng giá (giá mới = null). */
    REMOVED,
    /** Bảng giá đổi trạng thái; không gắn với Mặt hàng nào. */
    STATUS_CHANGED
}
