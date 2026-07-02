package org.hvtra.paymentservice.domain.enums;

public enum PaymentProvider {
    INTERNAL,   // dùng cho cash, admin xác nhận, nhân viên giao hàng thu tiền
    SEPAY,      // webhook chuyển khoản
    MANUAL      // admin đối soát thủ công nếu cần
}