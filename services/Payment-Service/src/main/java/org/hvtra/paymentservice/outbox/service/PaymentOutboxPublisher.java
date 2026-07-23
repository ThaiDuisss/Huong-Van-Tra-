package org.hvtra.paymentservice.outbox.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.kafka.outbox.dto.OutboxSaveCommand;
import org.hvtra.commonservice.kafka.outbox.service.OutboxEventService;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.event.PaymentOrderEventPayload;
import org.hvtra.paymentservice.outbox.resolver.PaymentOutboxTopicResolver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentOutboxPublisher {

    private static final String SOURCE = "payment-service";
    private static final String AGGREGATE_TYPE = "PAYMENT";

    public static final String PAYMENT_WAITING_PAYMENT = "PAYMENT_WAITING_PAYMENT";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_EXPIRED = "PAYMENT_EXPIRED";
    public static final String PAYMENT_CANCELLED = "PAYMENT_CANCELLED";
    public static final String PAYMENT_NEED_REVIEW = "PAYMENT_NEED_REVIEW";

    private final OutboxEventService<OutboxEvent> paymentOutboxEventService;
    private final PaymentOutboxTopicResolver paymentOutboxTopicResolver;

    /**
     * BANK_TRANSFER vừa tạo QR / attempt.
     *
     * Event này để Order-Service chuyển trạng thái paymentStatus sang WAITING_PAYMENT.
     * Vì bank transfer có thể tạo nhiều attempt, key nên gắn với paymentId + attemptNo.
     */
    public void publishWaitingPayment(Payment payment, PaymentAttempt attempt) {
        String businessKey = paymentAttemptBusinessKey(payment, attempt);
        UUID eventId = stableEventId(PAYMENT_WAITING_PAYMENT, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_WAITING_PAYMENT,
                eventId,
                payment,
                attempt,
                null
        );

        save(payment, PAYMENT_WAITING_PAYMENT, businessKey, eventId, payload);
    }

    /**
     * Payment thanh toán thành công.
     */
    public void publishSucceeded(Payment payment, PaymentTransaction transaction) {
        String businessKey = paymentStateBusinessKey(payment);
        UUID eventId = stableEventId(PAYMENT_SUCCEEDED, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_SUCCEEDED,
                eventId,
                payment,
                transaction == null ? null : transaction.getAttempt(),
                transaction
        );

        save(payment, PAYMENT_SUCCEEDED, businessKey, eventId, payload);
    }

    /**
     * Payment thất bại.
     */
    public void publishFailed(Payment payment, String reason) {
        String businessKey = paymentStateBusinessKey(payment);
        UUID eventId = stableEventId(PAYMENT_FAILED, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_FAILED,
                eventId,
                payment,
                null,
                null
        );
        payload.setFailureReason(reason);

        save(payment, PAYMENT_FAILED, businessKey, eventId, payload);
    }

    /**
     * Payment hết hạn.
     */
    public void publishExpired(Payment payment) {
        String businessKey = paymentStateBusinessKey(payment);
        UUID eventId = stableEventId(PAYMENT_EXPIRED, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_EXPIRED,
                eventId,
                payment,
                null,
                null
        );

        save(payment, PAYMENT_EXPIRED, businessKey, eventId, payload);
    }

    /**
     * Payment bị huỷ.
     */
    public void publishCancelled(Payment payment) {
        String businessKey = paymentStateBusinessKey(payment);
        UUID eventId = stableEventId(PAYMENT_CANCELLED, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_CANCELLED,
                eventId,
                payment,
                null,
                null
        );

        save(payment, PAYMENT_CANCELLED, businessKey, eventId, payload);
    }

    /**
     * Giao dịch nhận được nhưng cần review.
     *
     * Ví dụ:
     * - chuyển thiếu tiền
     * - sai nội dung chuyển khoản
     * - không match được payment/attempt
     */
    public void publishNeedReview(
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction,
            String reviewReason
    ) {
        String businessKey = payment.getId() + ":transaction:" + safeId(transaction);
        UUID eventId = stableEventId(PAYMENT_NEED_REVIEW, businessKey);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_NEED_REVIEW,
                eventId,
                payment,
                attempt,
                transaction
        );
        payload.setFailureReason(reviewReason);

        save(payment, PAYMENT_NEED_REVIEW, businessKey, eventId, payload);
    }

    private void save(
            Payment payment,
            String eventType,
            String businessKey,
            UUID eventId,
            PaymentOrderEventPayload payload
    ) {
        String messageKey = resolveMessageKey(payment);
        String topic = paymentOutboxTopicResolver.resolveTopic(eventType);
        payload.setEventId(toString(eventId));
        payload.setEventKey(businessKey);

        paymentOutboxEventService.saveEventIfAbsent(new OutboxSaveCommand(
                toString(eventId),
                AGGREGATE_TYPE,
                toString(payment.getId()),
                messageKey,
                eventType,
                businessKey,
                topic,
                SOURCE,
                toString(payment.getSagaId()),
                null,
                null,
                payload,
                buildHeaders(payment, eventType, eventId, messageKey, businessKey, topic)
        ));
    }

    private PaymentOrderEventPayload buildPayload(
            String eventType,
            UUID eventId,
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction
    ) {
        return PaymentOrderEventPayload.builder()
                .eventId(toString(eventId))
                .eventType(eventType)
                .eventKey(null)

                .sagaId(toString(payment.getSagaId()))

                .orderId(toString(payment.getOrderId()))
                .orderCode(payment.getOrderCode())
                .customerId(toString(payment.getCustomerId()))

                .paymentId(toString(payment.getId()))
                .paymentCode(payment.getPaymentCode())

                .attemptId(attempt == null ? null : toString(attempt.getId()))
                .attemptNo(attempt == null ? null : attempt.getAttemptNo())

                .transactionId(transaction == null ? null : toString(transaction.getId()))

                .paymentMethod(payment.getMethod() == null ? null : payment.getMethod().name())
                .paymentProvider(payment.getProvider() == null ? null : payment.getProvider().name())
                .paymentStatus(payment.getStatus() == null ? null : payment.getStatus().name())

                .amountRequired(payment.getAmountRequired())
                .amountPaid(resolveAmountPaid(payment, transaction))

                .currency(payment.getCurrency())

                .transferContent(resolveTransferContent(attempt, transaction))

                .paidAt(resolvePaidAt(payment, transaction))
                .expiresAt(resolveExpiresAt(payment, attempt))

                .failureReason(payment.getFailureReason())
                .occurredAt(Instant.now())
                .build();
    }

    private Map<String, Object> buildHeaders(
            Payment payment,
            String eventType,
            UUID eventId,
            String messageKey,
            String businessKey,
            String topic
    ) {
        Map<String, Object> headers = new LinkedHashMap<>();

        headers.put("source", SOURCE);
        headers.put("sourceService", SOURCE);
        headers.put("aggregateType", AGGREGATE_TYPE);
        headers.put("aggregateId", toString(payment.getId()));
        headers.put("eventId", toString(eventId));
        headers.put("event_id", toString(eventId));
        headers.put("eventType", eventType);
        headers.put("eventKey", businessKey);
        headers.put("messageKey", messageKey);
        headers.put("businessKey", businessKey);
        headers.put("topic", topic);
        headers.put("sagaId", toString(payment.getSagaId()));
        headers.put("orderId", toString(payment.getOrderId()));
        headers.put("orderCode", payment.getOrderCode());
        headers.put("paymentId", toString(payment.getId()));

        return headers;
    }

    /**
     * ID ổn định theo business event.
     *
     * Ví dụ:
     * PAYMENT_SUCCEEDED:{paymentId}
     * PAYMENT_WAITING_PAYMENT:{paymentId}:attempt:{attemptNo}
     */
    private UUID stableEventId(String eventType, String uniqueBusinessKey) {
        return UUID.nameUUIDFromBytes(
                (eventType + ":" + uniqueBusinessKey).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String paymentStateBusinessKey(Payment payment) {
        return payment.getId().toString();
    }

    private String paymentAttemptBusinessKey(Payment payment, PaymentAttempt attempt) {
        return payment.getId() + ":attempt:" + attempt.getAttemptNo();
    }

    private String resolveMessageKey(Payment payment) {
        String orderId = toString(payment.getOrderId());
        return orderId == null ? toString(payment.getId()) : orderId;
    }

    private BigDecimal resolveAmountPaid(Payment payment, PaymentTransaction transaction) {
        if (transaction != null && transaction.getAmount() != null) {
            return transaction.getAmount();
        }

        return payment.getAmountPaid();
    }

    private String resolveTransferContent(PaymentAttempt attempt, PaymentTransaction transaction) {
        if (transaction != null && transaction.getTransferContent() != null) {
            return transaction.getTransferContent();
        }

        if (attempt != null) {
            return attempt.getTransferContent();
        }

        return null;
    }

    private LocalDateTime resolvePaidAt(Payment payment, PaymentTransaction transaction) {
        if (transaction != null && transaction.getPaidAt() != null) {
            return transaction.getPaidAt();
        }

        return payment.getCompletedAt();
    }

    private LocalDateTime resolveExpiresAt(Payment payment, PaymentAttempt attempt) {
        if (attempt != null && attempt.getExpiresAt() != null) {
            return attempt.getExpiresAt();
        }

        return payment.getExpiresAt();
    }

    private String safeId(PaymentTransaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            return "unknown";
        }

        return transaction.getId().toString();
    }

    private String toString(UUID value) {
        return value == null ? null : value.toString();
    }
}
