package org.hvtra.commonservice.kafka.producer.service;

import org.hvtra.commonservice.kafka.producer.dto.SagaContext;

public interface SagaStep<C extends SagaContext> {

    String stepName();

    void process(C context);

    void compensate(C context);
}
