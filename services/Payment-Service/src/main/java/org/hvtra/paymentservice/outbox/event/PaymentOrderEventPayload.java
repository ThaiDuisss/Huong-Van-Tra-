package org.hvtra.paymentservice.outbox.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOrderEventPayload {

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