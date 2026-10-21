package org.hvtra.orderservice.domain.enums;

public enum PaymentStatus {
    UNPAID,
    WAITING_PAYMENT,
    PAID,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
    /** Đơn đặt trước mới thu tiền cọc. */
    PARTIALLY_PAID,
    /** Tiền chuyển khoản về nhưng lệch số tiền hoặc về sau khi QR hết hạn: giữ đơn, chờ kiểm tra tay. */
    NEED_REVIEW
}