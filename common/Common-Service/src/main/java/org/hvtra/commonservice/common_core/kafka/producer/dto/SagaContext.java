
package org.hvtra.commonservice.common_core.kafka.producer.dto;

import java.util.UUID;

public interface SagaContext {
    UUID sagaId();
    UUID aggregateId();
}