package org.hvtra.commonservice.common_core.kafka.inbox;

public enum InboxStatus {
    /**
     * Vừa nhận message.
     */
    RECEIVED,

    /**
     * Đang xử lý.
     */
    PROCESSING,

    /**
     * Xử lý thành công.
     */
    PROCESSED,

    /**
     * Xử lý lỗi, còn retry được.
     */
    FAILED,

    /**
     * Bỏ qua có chủ đích.
     */
    IGNORED,

    /**
     * Lỗi quá số lần retry.
     */
    DEAD
}