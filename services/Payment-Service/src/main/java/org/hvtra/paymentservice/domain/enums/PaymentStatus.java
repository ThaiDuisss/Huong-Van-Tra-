package org.hvtra.paymentservice.domain.enums;

public enum PaymentStatus {
    PENDING,              // mới tạo, chưa có tiền
    WAITING_PAYMENT,      // đang chờ khách thanh toán/chuyển khoản
    WAITING_COLLECTION,   // đơn cash/COD, chờ nhân viên/shipper thu tiền
    PARTIALLY_PAID,       // trả thiếu
    PAID,                 // đủ tiền
    OVERPAID,             // chuyển thừa
    FAILED,
    EXPIRED,
    CANCELLED,
    NEED_REVIEW,          // lệch tiền, không match nội dung, duplicate nghi ngờ
    REFUND_PENDING,
    PARTIALLY_REFUNDED,
    REFUNDED
}