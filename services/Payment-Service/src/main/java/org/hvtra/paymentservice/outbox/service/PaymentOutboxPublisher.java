package org.hvtra.paymentservice.outbox.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.*;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.event.PaymentOrderEventPayload;
import org.hvtra.paymentservice.outbox.factory.PaymentOutboxEventFactory;
import org.hvtra.paymentservice.outbox.repository.OutboxEventRepository;
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

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentOutboxEventFactory paymentOutboxEventFactory;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * BANK_TRANSFER vừa tạo QR / attempt.
     *
     * Event này để Order-Service chuyển trạng thái paymentStatus sang WAITING_PAYMENT.
     * Vì bank transfer có thể tạo nhiều attempt, key nên gắn với paymentId + attemptNo.
     */
    public void publishWaitingPayment(Payment payment, PaymentAttempt attempt) {
        UUID eventKey = paymentAttemptEventKey(PAYMENT_WAITING_PAYMENT, payment, attempt);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_WAITING_PAYMENT,
                eventKey,
                payment,
                attempt,
                null
        );

        save(payment, PAYMENT_WAITING_PAYMENT, eventKey, payload);
    }

    /**
     * Payment đã thanh toán thành công.
     *
     * Cash sẽ gọi event này ngay sau khi tạo transaction thành công.
     * Bank transfer sẽ gọi event này sau khi SePay webhook matched đúng payment.
     */
    public void publishSucceeded(Payment payment, PaymentTransaction transaction) {
        UUID eventKey = paymentStateEventKey(PAYMENT_SUCCEEDED, payment);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_SUCCEEDED,
                eventKey,
                payment,
                transaction == null ? null : transaction.getAttempt(),
                transaction
        );

        save(payment, PAYMENT_SUCCEEDED, eventKey, payload);
    }

    /**
     * Payment thất bại.
     */
    public void publishFailed(Payment payment, String reason) {
        UUID eventKey = paymentStateEventKey(PAYMENT_FAILED, payment);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_FAILED,
                eventKey,
                payment,
                null,
                null
        );
        payload.setFailureReason(reason);

        save(payment, PAYMENT_FAILED, eventKey, payload);
    }

    /**
     * Payment hết hạn.
     */
    public void publishExpired(Payment payment) {
        UUID eventKey = paymentStateEventKey(PAYMENT_EXPIRED, payment);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_EXPIRED,
                eventKey,
                payment,
                null,
                null
        );

        save(payment, PAYMENT_EXPIRED, eventKey, payload);
    }

    /**
     * Payment bị huỷ.
     */
    public void publishCancelled(Payment payment) {
        UUID eventKey = paymentStateEventKey(PAYMENT_CANCELLED, payment);

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_CANCELLED,
                eventKey,
                payment,
                null,
                null
        );

        save(payment, PAYMENT_CANCELLED, eventKey, payload);
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
        UUID eventKey = stableEventKey(
                PAYMENT_NEED_REVIEW,
                payment.getId() + ":transaction:" + safeId(transaction)
        );

        PaymentOrderEventPayload payload = buildPayload(
                PAYMENT_NEED_REVIEW,
                eventKey,
                payment,
                attempt,
                transaction
        );
        payload.setFailureReason(reviewReason);

        save(payment, PAYMENT_NEED_REVIEW, eventKey, payload);
    }

    private void save(
            Payment payment,
            String eventType,
            UUID eventKey,
            PaymentOrderEventPayload payload
    ) {
        /*
         * Check mềm để tránh insert lại cùng business event trong cùng service flow.
         * Check cứng vẫn là unique constraint ở DB: event_type + event_key.
         */
        if (existsOutboxEvent(eventType, eventKey)) {
            return;
        }

        OutboxEvent outboxEvent = paymentOutboxEventFactory.create(
                AGGREGATE_TYPE,
                payment.getId(),
                eventType,
                eventKey,
                payload,
                buildHeaders(payment, eventType, eventKey)
        );

        outboxEventRepository.save(outboxEvent);
    }

    private PaymentOrderEventPayload buildPayload(
            String eventType,
            UUID eventKey,
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction
    ) {
        return PaymentOrderEventPayload.builder()
                .eventType(eventType)
                .eventKey(toString(eventKey))

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

    private Map<String, Object> buildHeaders(Payment payment, String eventType, UUID eventKey) {
        Map<String, Object> headers = new LinkedHashMap<>();

        headers.put("source", SOURCE);
        headers.put("aggregateType", AGGREGATE_TYPE);
        headers.put("aggregateId", toString(payment.getId()));
        headers.put("eventType", eventType);
        headers.put("eventKey", toString(eventKey));
        headers.put("sagaId", toString(payment.getSagaId()));
        headers.put("orderId", toString(payment.getOrderId()));
        headers.put("orderCode", payment.getOrderCode());
        headers.put("paymentId", toString(payment.getId()));

        return headers;
    }

    /**
     * Key ổn định theo business event.
     *
     * Ví dụ:
     * PAYMENT_SUCCEEDED:{paymentId}
     * PAYMENT_WAITING_PAYMENT:{paymentId}:attempt:{attemptNo}
     */
    private UUID stableEventKey(String eventType, String uniqueBusinessKey) {
        return UUID.nameUUIDFromBytes(
                (eventType + ":" + uniqueBusinessKey).getBytes(StandardCharsets.UTF_8)
        );
    }

    private UUID paymentStateEventKey(String eventType, Payment payment) {
        return stableEventKey(eventType, payment.getId().toString());
    }

    private UUID paymentAttemptEventKey(String eventType, Payment payment, PaymentAttempt attempt) {
        String businessKey = payment.getId() + ":attempt:" + attempt.getAttemptNo();
        return stableEventKey(eventType, businessKey);
    }

    private boolean existsOutboxEvent(String eventType, UUID eventKey) {
        Long count = entityManager.createQuery("""
                        select count(e.id)
                        from OutboxEvent e
                        where e.eventType = :eventType
                          and e.eventKey = :eventKey
                        """, Long.class)
                .setParameter("eventType", eventType)
                .setParameter("eventKey", eventKey)
                .getSingleResult();

        return count != null && count > 0;
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