package org.hvtra.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.entity.PaymentWebhookEvent;
import org.hvtra.paymentservice.domain.enums.*;
import org.hvtra.paymentservice.dto.event.PaymentEventPayload;
import org.hvtra.paymentservice.dto.request.SepayWebhookRequest;
import org.hvtra.paymentservice.dto.response.SepayWebhookResponse;
import org.hvtra.paymentservice.outbox.entity.OutboxEvent;
import org.hvtra.paymentservice.outbox.event.PaymentEventType;
import org.hvtra.paymentservice.outbox.factory.PaymentOutboxEventFactory;
import org.hvtra.paymentservice.outbox.repository.OutboxEventRepository;
import org.hvtra.paymentservice.repository.PaymentAttemptRepository;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.repository.PaymentTransactionRepository;
import org.hvtra.paymentservice.repository.PaymentWebhookEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SepayWebhookService {

    private final SepayProperties properties;
    private static final String CURRENCY_VND = "VND";
    private static final String TRANSFER_IN = "in";
    private static final DateTimeFormatter SEPAY_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Payment code hiện tại của bạn đang generate dạng HVTRA + yyMMddHHmmssSSS.
     * Regex này dùng để fallback nếu SePay không fill field code.
     */
    private static final Pattern PAYMENT_CODE_PATTERN =
            Pattern.compile("\\bHVTRA[A-Z0-9]{6,30}\\b", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentOutboxEventFactory paymentOutboxEventFactory;

    @Transactional
    public SepayWebhookResponse handle(
            String rawBody,
            String signature,
            String timestamp,
            String authorization
    ) {
        verifyAuthorization(authorization);

        SepayWebhookRequest request = parseRequest(rawBody);
        String providerEventId = validateAndGetProviderEventId(request);

        if (paymentWebhookEventRepository.existsByProviderAndProviderEventId(
                PaymentProvider.SEPAY,
                providerEventId
        )) {
            return SepayWebhookResponse.of(true);
        }

        PaymentWebhookEvent webhookEvent = createWebhookEvent(request, rawBody, providerEventId);
        paymentWebhookEventRepository.save(webhookEvent);

        if (!TRANSFER_IN.equalsIgnoreCase(request.getTransferType())) {
            webhookEvent.markFailed("Bỏ qua giao dịch tiền ra: " + request.getTransferType());
            paymentWebhookEventRepository.save(webhookEvent);
            return SepayWebhookResponse.of(true);
        }

        String paymentCode = resolvePaymentCode(request);
        webhookEvent.setPaymentCode(paymentCode);

        if (!StringUtils.hasText(paymentCode)) {
            webhookEvent.markNeedReview("Không tìm thấy paymentCode trong webhook", null);
            paymentWebhookEventRepository.save(webhookEvent);
            return SepayWebhookResponse.of(true);
        }

        PaymentAttempt attempt = paymentAttemptRepository
                .findTopByTransferContentAndProviderOrderByCreatedAtDesc(
                        paymentCode,
                        PaymentProvider.SEPAY
                )
                .orElse(null);

        if (attempt == null) {
            webhookEvent.markNeedReview("Không tìm thấy payment attempt với paymentCode=" + paymentCode, null);
            paymentWebhookEventRepository.save(webhookEvent);
            return SepayWebhookResponse.of(true);
        }

        Payment payment = attempt.getPayment();
        webhookEvent.setMatchedPaymentId(payment.getId());

        String externalTransactionId = providerEventId;

        if (paymentTransactionRepository.existsByProviderAndExternalTransactionId(
                PaymentProvider.SEPAY,
                externalTransactionId
        )) {
            webhookEvent.markProcessed(payment.getId());
            paymentWebhookEventRepository.save(webhookEvent);
            return SepayWebhookResponse.of(true);
        }

        BigDecimal paidAmount = normalizeMoney(request.getTransferAmount());
        BigDecimal expectedAmount = normalizeMoney(attempt.getAmountExpected());
        LocalDateTime paidAt = parseTransactionDate(request.getTransactionDate());

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .attempt(attempt)
                .transactionType(PaymentTransactionType.PAYMENT)
                .method(PaymentMethod.BANK_TRANSFER)
                .provider(PaymentProvider.SEPAY)
                .status(PaymentTransactionStatus.RECEIVED)
                .amount(paidAmount)
                .currency(CURRENCY_VND)
                .externalTransactionId(externalTransactionId)
                .referenceCode(request.getReferenceCode())
                .gateway(request.getGateway())
                .bankAccountNumber(request.getAccountNumber())
                .transferContent(request.getContent())
                .paidAt(paidAt)
                .rawPayload(rawBody)
                .build();

        if (paidAmount.compareTo(expectedAmount) == 0) {
            markPaymentSucceeded(payment, attempt, transaction, paidAmount, paidAt);
            webhookEvent.markProcessed(payment.getId());

            PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
            paymentAttemptRepository.save(attempt);
            Payment savedPayment = paymentRepository.save(payment);
            paymentWebhookEventRepository.save(webhookEvent);

            savePaymentOutboxEvent(
                    savedPayment,
                    PaymentEventType.PAYMENT_SUCCEEDED,
                    stableEventKey(PaymentEventType.PAYMENT_SUCCEEDED, savedPayment.getId().toString()),
                    toEventPayload(savedPayment, attempt, savedTransaction, null)
            );

            return SepayWebhookResponse.of(true);
        }

        String reason = "Số tiền chuyển khoản không khớp. Expected="
                + expectedAmount
                + ", actual="
                + paidAmount;

        markPaymentNeedReview(payment, attempt, transaction, paidAmount, reason);
        webhookEvent.markNeedReview(reason, payment.getId());

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);
        paymentAttemptRepository.save(attempt);
        Payment savedPayment = paymentRepository.save(payment);
        paymentWebhookEventRepository.save(webhookEvent);

        savePaymentOutboxEvent(
                savedPayment,
                PaymentEventType.PAYMENT_NEED_REVIEW,
                stableEventKey(PaymentEventType.PAYMENT_NEED_REVIEW, savedTransaction.getId().toString()),
                toEventPayload(savedPayment, attempt, savedTransaction, reason)
        );

        return SepayWebhookResponse.of(true);
    }

    private void verifyAuthorization(String authorization) {
        String secret = properties.getSecret();

        if (!StringUtils.hasText(secret)) {
            return;
        }

        String expectedApiKeyHeader = "Apikey " + secret;
        String expectedBearerHeader = "Bearer " + secret;

        if (!expectedApiKeyHeader.equals(authorization)
                && !expectedBearerHeader.equals(authorization)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid SePay webhook authorization"
            );
        }
    }


    private PaymentEventPayload toEventPayload(
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction,
            String reason
    ) {
        return new PaymentEventPayload(
                payment.getId(),
                attempt == null ? null : attempt.getId(),
                transaction == null ? null : transaction.getId(),
                payment.getPaymentCode(),
                payment.getOrderId(),
                payment.getOrderCode(),
                payment.getCustomerId(),
                payment.getSagaId(),
                payment.getMethod().name(),
                payment.getProvider().name(),
                payment.getStatus().name(),
                payment.getAmountRequired(),
                payment.getAmountPaid(),
                payment.getCurrency(),
                transaction == null ? payment.getCompletedAt() : transaction.getPaidAt(),
                payment.getExpiresAt(),
                reason
        );
    }

    private SepayWebhookRequest parseRequest(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, SepayWebhookRequest.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid SePay webhook payload",
                    ex
            );
        }
    }

    private String validateAndGetProviderEventId(SepayWebhookRequest request) {
        if (request == null || request.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SePay webhook id không được null"
            );
        }

        if (request.getTransferAmount() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "SePay transferAmount không được null"
            );
        }

        return String.valueOf(request.getId());
    }

    private PaymentWebhookEvent createWebhookEvent(
            SepayWebhookRequest request,
            String rawBody,
            String providerEventId
    ) {
        return PaymentWebhookEvent.builder()
                .provider(PaymentProvider.SEPAY)
                .providerEventId(providerEventId)
                .referenceCode(request.getReferenceCode())
                .paymentCode(resolvePaymentCode(request))
                .status(SepayWebhookStatus.RECEIVED)
                .transferType(request.getTransferType())
                .transferAmount(normalizeMoney(request.getTransferAmount()))
                .transactionDate(parseTransactionDate(request.getTransactionDate()))
                .rawPayload(rawBody)
                .build();
    }

    private void markPaymentSucceeded(
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction,
            BigDecimal paidAmount,
            LocalDateTime paidAt
    ) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmountPaid(paidAmount);
        payment.setFailureReason(null);
        payment.setReviewReason(null);
        payment.setCompletedAt(paidAt == null ? LocalDateTime.now() : paidAt);

        attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
        attempt.setCompletedAt(LocalDateTime.now());

        transaction.setStatus(PaymentTransactionStatus.MATCHED);
        transaction.setReviewReason(null);
    }

    private void markPaymentNeedReview(
            Payment payment,
            PaymentAttempt attempt,
            PaymentTransaction transaction,
            BigDecimal paidAmount,
            String reason
    ) {
        payment.setStatus(PaymentStatus.NEED_REVIEW);
        payment.setAmountPaid(paidAmount);
        payment.setReviewReason(reason);

        attempt.setStatus(PaymentAttemptStatus.NEED_REVIEW);

        transaction.setStatus(PaymentTransactionStatus.NEED_REVIEW);
        transaction.setReviewReason(reason);
    }


    private String resolvePaymentCode(SepayWebhookRequest request) {
        if (request == null) {
            return null;
        }

        if (StringUtils.hasText(request.getCode())) {
            return request.getCode().trim().toUpperCase();
        }

        return extractPaymentCodeFromContent(request.getContent());
    }

    private String extractPaymentCodeFromContent(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        Matcher matcher = PAYMENT_CODE_PATTERN.matcher(content.toUpperCase());
        if (!matcher.find()) {
            return null;
        }

        return matcher.group();
    }

    private LocalDateTime parseTransactionDate(String transactionDate) {
        if (!StringUtils.hasText(transactionDate)) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(transactionDate, SEPAY_DATE_TIME_FORMAT);
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private void savePaymentOutboxEvent(
            Payment payment,
            String eventType,
            UUID eventKey,
            PaymentEventPayload payload
    ) {
        Map<String, Object> headers = Map.of(
                "sourceService", "Payment-Service",
                "sagaId", payment.getSagaId().toString(),
                "orderId", payment.getOrderId().toString()
        );

        OutboxEvent event = paymentOutboxEventFactory.create(
                "Payment",
                payment.getId(),
                eventType,
                eventKey,
                payload,
                headers
        );

        event.setCorrelationId(payment.getSagaId().toString());

        outboxEventRepository.save(event);
    }

    private UUID stableEventKey(String eventType, String uniqueBusinessKey) {
        return UUID.nameUUIDFromBytes(
                (eventType + ":" + uniqueBusinessKey).getBytes(StandardCharsets.UTF_8)
        );
    }
}