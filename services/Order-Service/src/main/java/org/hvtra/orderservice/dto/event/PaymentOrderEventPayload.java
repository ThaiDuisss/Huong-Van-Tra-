package org.hvtra.orderservice.dto.event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOrderEventPayload implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String sagaId;
    String orderId;
    String orderCode;
    String paymentId;
    String paymentCode;
    String transactionId;
    String paymentMethod;
    String paymentProvider;
    String paymentStatus;
    BigDecimal amountRequired;
    BigDecimal amountPaid;
    String currency;
    String transferContent;
    String failureReason;
}
