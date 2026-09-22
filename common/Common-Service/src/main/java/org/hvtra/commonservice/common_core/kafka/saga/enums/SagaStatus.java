package org.hvtra.commonservice.common_core.kafka.saga.enums;

public enum SagaStatus {
    /**
     * Saga vừa được tạo.
     */
    STARTED,

    /**
     * Saga đang chạy.
     */
    RUNNING,

    /**
     * Saga hoàn thành toàn bộ.
     */
    COMPLETED,

    /**
     * Saga lỗi.
     */
    FAILED,

    /**
     * Đang rollback/compensate.
     */
    COMPENSATING,

    /**
     * Rollback/compensate xong.
     */
    COMPENSATED,

    /**
     * Saga bị hủy chủ động.
     */
    CANCELLED
}
