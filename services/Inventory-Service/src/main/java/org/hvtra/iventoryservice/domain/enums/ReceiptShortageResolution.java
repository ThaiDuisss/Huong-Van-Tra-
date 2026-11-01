package org.hvtra.iventoryservice.domain.enums;

/** Cách xử lý dòng phiếu nhập giao thiếu (thực nhận < số đặt). */
public enum ReceiptShortageResolution {
    /** Nhập số thực nhận và đóng dòng, không chờ giao bù. */
    ACCEPT_PARTIAL_CLOSE,
    /** Từ chối nhận cả dòng: không nhập kho, toàn bộ hàng đạt QC trả lại nhà cung cấp. */
    REJECT_LINE
}
