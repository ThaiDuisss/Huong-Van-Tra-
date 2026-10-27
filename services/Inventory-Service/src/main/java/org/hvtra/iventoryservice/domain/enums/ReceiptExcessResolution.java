package org.hvtra.iventoryservice.domain.enums;

/** Cách xử lý dòng phiếu nhập giao thừa (thực nhận > số đặt). */
public enum ReceiptExcessResolution {
    /** Nhập kho toàn bộ số đạt QC, kể cả phần vượt số đặt. */
    ACCEPT_ALL,
    /** Chỉ nhập đúng số đặt, phần vượt lập biên bản trả lại nhà cung cấp. */
    ACCEPT_ORDERED_ONLY
}
