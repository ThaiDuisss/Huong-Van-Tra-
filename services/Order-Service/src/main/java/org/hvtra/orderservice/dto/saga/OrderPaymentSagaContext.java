package org.hvtra.orderservice.dto.saga;

import org.hvtra.commonservice.common_core.kafka.saga.service.SagaContext;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderPaymentSagaContext(
        UUID sagaId,
        UUID entityId,
        UUID orderId,
        String orderCode,
        String paymentMethod,
        String paymentCode,
        BigDecimal finalAmount
) implements SagaContext {
}
