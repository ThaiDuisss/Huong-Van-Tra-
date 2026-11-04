package org.hvtra.orderservice.domain.enums;

public enum OrderImportStatus {
    /** Dry run: đơn hợp lệ, sẽ được tạo khi nhập. */
    VALID,
    /** Đã tạo đơn. */
    IMPORTED,
    /** Mã đơn tham chiếu đã được nhập trước đó, bỏ qua để không tạo trùng. */
    DUPLICATE,
    /** Dữ liệu không hợp lệ. */
    INVALID,
    /** Dữ liệu hợp lệ nhưng tạo đơn thất bại. */
    FAILED
}
