
package org.hvtra.commonservice.kafka.producer.dto;

import java.util.UUID;

public interface SagaContext {
    UUID sagaId();
    UUID aggregateId();
}