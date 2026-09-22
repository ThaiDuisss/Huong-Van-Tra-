package org.hvtra.commonservice.common_core.kafka.saga.enums;

public enum SagaStepStatus {

    /**
     * Step chưa chạy.
     */
    PENDING,

    /**
     * Step đang chạy.
     */
    PROCESSING,

    /**
     * Step thành công.
     */
    COMPLETED,

    /**
     * Step lỗi.
     */
    FAILED,

    /**
     * Step đang rollback.
     */
    COMPENSATING,

    /**
     * Rollback step thành công.
     */
    COMPENSATED,

    /**
     * Step bị skip.
     */
    SKIPPED
}