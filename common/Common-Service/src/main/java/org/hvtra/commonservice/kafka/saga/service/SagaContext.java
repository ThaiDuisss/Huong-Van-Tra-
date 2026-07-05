package org.hvtra.commonservice.kafka.saga.service;

import java.util.UUID;

/**
 * Dữ liệu đầu vào chung cho saga.
 *
 * Đây không phải DDD context.
 * Nó chỉ là contract để các saga data có tối thiểu:
 * - sagaId
 * - entityId
 */
public interface SagaContext {

    UUID sagaId();

    UUID entityId();
}