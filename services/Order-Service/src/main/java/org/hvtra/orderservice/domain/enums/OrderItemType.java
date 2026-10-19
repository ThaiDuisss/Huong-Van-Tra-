package org.hvtra.orderservice.domain.enums;

public enum OrderItemType {
    /** Mặt hàng bán thường (thành phẩm / BOM). */
    PRODUCT,
    /** Trà đóng theo yêu cầu: khách tự chọn nguyên liệu, kho đóng gói rồi mới giao. */
    CUSTOM_BUNDLE
}
