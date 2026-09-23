package org.hvtra.paymentservice.outbox.event;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOrderEventPayload implements CommonEventPayload {

    String eventId;
    String eventType;
    String eventKey;

    String sagaId;

    String orderId;
    String orderCode;
    String customerId;

    String paymentId;
    String paymentCode;

    String attemptId;
    Integer attemptNo;

    String transactionId;

    String paymentMethod;
    String paymentProvider;
    String paymentStatus;

    BigDecimal amountRequired;
    BigDecimal amountPaid;

    String currency;
    String transferContent;

    LocalDateTime paidAt;
    LocalDateTime expiresAt;

    String failureReason;
    Instant occurredAt;
}
