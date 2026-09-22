package org.hvtra.commonservice.common_core.kafka.saga.service;

/**
 * Contract generic cho 1 bước saga.
 *
 * Common không xử lý business.
 * Service nào dùng thì implements.
 */
public interface SagaStep<C extends SagaContext> {

    /**
     * Tên step.
     */
    String stepName();

    /**
     * Xử lý step chính.
     */
    void process(C context);

    /**
     * Rollback/compensate step nếu saga lỗi.
     */
    void compensate(C context);
}