package org.hvtra.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.entity.PaymentWebhookEvent;
import org.hvtra.paymentservice.domain.enums.PaymentAttemptStatus;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.SepayWebhookStatus;
import org.hvtra.paymentservice.dto.response.SepayWebhookResponse;
import org.hvtra.paymentservice.outbox.service.PaymentOutboxPublisher;
import org.hvtra.paymentservice.repository.PaymentAttemptRepository;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.repository.PaymentTransactionRepository;
import org.hvtra.paymentservice.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SepayWebhookServiceTest {

    private static final String SECRET = "test-sepay-key";
    private static final String API_KEY_HEADER = "Apikey " + SECRET;
    private static final String PAYMENT_CODE = "HVTRA261026093000123";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Mock
    private PaymentOutboxPublisher paymentOutboxPublisher;

    private SepayProperties properties;
    private SepayWebhookService service;

    @BeforeEach
    void setUp() {
        properties = new SepayProperties();
        properties.setSecret(SECRET);
        service = new SepayWebhookService(
                properties,
                new ObjectMapper(),
                paymentRepository,
                paymentAttemptRepository,
                paymentTransactionRepository,
                paymentWebhookEventRepository,
                paymentOutboxPublisher
        );
        lenient().when(paymentWebhookEventRepository.save(any(PaymentWebhookEvent.class))).then(returnsFirstArg());
        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).then(returnsFirstArg());
        lenient().when(paymentAttemptRepository.save(any(PaymentAttempt.class))).then(returnsFirstArg());
        lenient().when(paymentRepository.save(any(Payment.class))).then(returnsFirstArg());
    }

    @Test
    @DisplayName("Sai hoặc thiếu API key thì trả 401 và không ghi gì")
    void rejectsMissingOrWrongAuthorization() {
        String body = webhookBody(1L, "in", "100000", PAYMENT_CODE, null);

        assertStatus(() -> service.handle(body, null, null, null), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> service.handle(body, null, null, "Apikey wrong"), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> service.handle(body, null, null, SECRET), HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(paymentWebhookEventRepository, paymentAttemptRepository, paymentOutboxPublisher);
    }

    @Test
    @DisplayName("Chấp nhận cả dạng 'Apikey <key>' và 'Bearer <key>'")
    void acceptsApikeyAndBearerAuthorization() {
        when(paymentWebhookEventRepository.existsByProviderAndProviderEventId(eq(PaymentProvider.SEPAY), anyString()))
                .thenReturn(true);

        assertThat(service.handle(webhookBody(1L, "in", "100000", PAYMENT_CODE, null), null, null, API_KEY_HEADER)
                .isSuccess()).isTrue();
        assertThat(service.handle(webhookBody(2L, "in", "100000", PAYMENT_CODE, null), null, null, "Bearer " + SECRET)
                .isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Chưa cấu hình secret (môi trường dev) thì bỏ qua xác thực")
    void skipsAuthorizationWhenSecretNotConfigured() {
        properties.setSecret(" ");
        when(paymentWebhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.SEPAY, "1"))
                .thenReturn(true);

        SepayWebhookResponse response = service.handle(webhookBody(1L, "in", "100000", PAYMENT_CODE, null),
                null, null, null);

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Payload không phải JSON, thiếu id hoặc thiếu số tiền thì trả 400")
    void rejectsInvalidPayload() {
        assertStatus(() -> service.handle("not-json", null, null, API_KEY_HEADER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.handle("{\"transferAmount\":1000}", null, null, API_KEY_HEADER),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.handle("{\"id\":5}", null, null, API_KEY_HEADER), HttpStatus.BAD_REQUEST);

        verify(paymentWebhookEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Webhook trùng id (SePay gửi lại) trả success, không xử lý lại")
    void duplicateWebhookIdIsAcknowledgedWithoutProcessing() {
        when(paymentWebhookEventRepository.existsByProviderAndProviderEventId(PaymentProvider.SEPAY, "77"))
                .thenReturn(true);

        SepayWebhookResponse response = service.handle(webhookBody(77L, "in", "100000", PAYMENT_CODE, null),
                null, null, API_KEY_HEADER);

        assertThat(response.isSuccess()).isTrue();
        verify(paymentWebhookEventRepository, never()).save(any());
        verifyNoInteractions(paymentAttemptRepository, paymentTransactionRepository, paymentOutboxPublisher);
    }

    @Test
    @DisplayName("Giao dịch tiền ra được ghi FAILED và bỏ qua")
    void outgoingTransferIsMarkedFailed() {
        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(10L, "out", "50000", PAYMENT_CODE, null));

        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("tiền ra");
        verifyNoInteractions(paymentAttemptRepository, paymentOutboxPublisher);
    }

    @Test
    @DisplayName("Không đọc được mã thanh toán thì chuyển NEED_REVIEW")
    void missingPaymentCodeNeedsReview() {
        PaymentWebhookEvent event = handleAndCaptureEvent(
                webhookBody(11L, "in", "100000", null, "CK tien tra sen"));

        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.NEED_REVIEW);
        assertThat(event.getPaymentCode()).isNull();
        verifyNoInteractions(paymentAttemptRepository, paymentOutboxPublisher);
    }

    @Test
    @DisplayName("Không có field code thì lấy mã HVTRA trong nội dung chuyển khoản (không phân biệt hoa thường)")
    void extractsPaymentCodeFromTransferContent() {
        when(paymentAttemptRepository.findTopByTransferContentAndProviderOrderByCreatedAtDesc(
                PAYMENT_CODE, PaymentProvider.SEPAY)).thenReturn(Optional.empty());

        PaymentWebhookEvent event = handleAndCaptureEvent(
                webhookBody(12L, "in", "100000", null, "NGUYEN VAN A chuyen tien " + PAYMENT_CODE.toLowerCase()));

        assertThat(event.getPaymentCode()).isEqualTo(PAYMENT_CODE);
        verify(paymentAttemptRepository).findTopByTransferContentAndProviderOrderByCreatedAtDesc(
                PAYMENT_CODE, PaymentProvider.SEPAY);
    }

    @Test
    @DisplayName("Mã thanh toán không khớp attempt nào thì NEED_REVIEW")
    void unknownPaymentCodeNeedsReview() {
        when(paymentAttemptRepository.findTopByTransferContentAndProviderOrderByCreatedAtDesc(
                PAYMENT_CODE, PaymentProvider.SEPAY)).thenReturn(Optional.empty());

        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(13L, "in", "100000", PAYMENT_CODE, null));

        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.NEED_REVIEW);
        assertThat(event.getErrorMessage()).contains(PAYMENT_CODE);
        verifyNoInteractions(paymentTransactionRepository, paymentOutboxPublisher);
    }

    @Test
    @DisplayName("Đúng số tiền: payment PAID, attempt SUCCEEDED, giao dịch MATCHED và phát event succeeded")
    void exactAmountMarksPaymentPaidAndPublishesSucceeded() {
        Payment payment = payment(PaymentStatus.WAITING_PAYMENT);
        PaymentAttempt attempt = attempt(payment, PaymentAttemptStatus.WAITING, "100000");
        stubAttempt(attempt);

        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(20L, "in", "100000", PAYMENT_CODE, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountPaid()).isEqualByComparingTo("100000");
        assertThat(payment.getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 9, 30, 15));
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.PROCESSED);
        assertThat(event.getMatchedPaymentId()).isEqualTo(payment.getId());

        PaymentTransaction transaction = captureTransaction();
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.MATCHED);
        assertThat(transaction.getExternalTransactionId()).isEqualTo("20");
        assertThat(transaction.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        verify(paymentOutboxPublisher).publishSucceeded(payment, transaction);
    }

    @Test
    @DisplayName("Số tiền lẻ được làm tròn về đồng trước khi so khớp")
    void amountIsRoundedToWholeDongBeforeMatching() {
        Payment payment = payment(PaymentStatus.WAITING_PAYMENT);
        stubAttempt(attempt(payment, PaymentAttemptStatus.WAITING, "100000.00"));

        handleAndCaptureEvent(webhookBody(21L, "in", "100000.40", PAYMENT_CODE, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("Chuyển thiếu hoặc thừa tiền: NEED_REVIEW và phát event need-review")
    void amountMismatchNeedsReview() {
        Payment payment = payment(PaymentStatus.WAITING_PAYMENT);
        PaymentAttempt attempt = attempt(payment, PaymentAttemptStatus.WAITING, "100000");
        stubAttempt(attempt);

        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(22L, "in", "90000", PAYMENT_CODE, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NEED_REVIEW);
        assertThat(payment.getReviewReason()).contains("Expected=100000").contains("actual=90000");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.NEED_REVIEW);
        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.NEED_REVIEW);
        PaymentTransaction transaction = captureTransaction();
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.NEED_REVIEW);
        verify(paymentOutboxPublisher).publishNeedReview(eq(payment), eq(attempt), eq(transaction), anyString());
        verify(paymentOutboxPublisher, never()).publishSucceeded(any(), any());
    }

    @Test
    @DisplayName("Tiền về khi QR đã hết hạn: không tự ghi PAID mà chờ đối soát")
    void moneyAfterExpiryNeedsReview() {
        Payment payment = payment(PaymentStatus.EXPIRED);
        stubAttempt(attempt(payment, PaymentAttemptStatus.EXPIRED, "100000"));

        handleAndCaptureEvent(webhookBody(23L, "in", "100000", PAYMENT_CODE, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NEED_REVIEW);
        assertThat(payment.getReviewReason()).contains("hết hạn");
        verify(paymentOutboxPublisher, never()).publishSucceeded(any(), any());
    }

    @Test
    @DisplayName("Tiền về khi thu ngân đã huỷ phiên: lý do ghi 'huỷ'")
    void moneyAfterCancellationNeedsReview() {
        Payment payment = payment(PaymentStatus.WAITING_PAYMENT);
        stubAttempt(attempt(payment, PaymentAttemptStatus.CANCELLED, "100000"));

        handleAndCaptureEvent(webhookBody(24L, "in", "100000", PAYMENT_CODE, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.NEED_REVIEW);
        assertThat(payment.getReviewReason()).contains("huỷ");
    }

    @Test
    @DisplayName("Payment đã PAID: giao dịch mới ghi DUPLICATE, không phát event")
    void paymentAlreadyPaidRecordsDuplicateTransaction() {
        Payment payment = payment(PaymentStatus.PAID);
        stubAttempt(attempt(payment, PaymentAttemptStatus.SUCCEEDED, "100000"));

        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(25L, "in", "100000", PAYMENT_CODE, null));

        assertThat(captureTransaction().getStatus()).isEqualTo(PaymentTransactionStatus.DUPLICATE);
        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.PROCESSED);
        verifyNoInteractions(paymentOutboxPublisher);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giao dịch SePay đã được ghi nhận trước đó thì không tạo giao dịch thứ hai")
    void existingTransactionIsNotRecordedTwice() {
        Payment payment = payment(PaymentStatus.WAITING_PAYMENT);
        stubAttempt(attempt(payment, PaymentAttemptStatus.WAITING, "100000"));
        when(paymentTransactionRepository.existsByProviderAndExternalTransactionId(PaymentProvider.SEPAY, "26"))
                .thenReturn(true);

        PaymentWebhookEvent event = handleAndCaptureEvent(webhookBody(26L, "in", "100000", PAYMENT_CODE, null));

        assertThat(event.getStatus()).isEqualTo(SepayWebhookStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_PAYMENT);
        verify(paymentTransactionRepository, never()).save(any());
        verifyNoInteractions(paymentOutboxPublisher);
    }

    private PaymentWebhookEvent handleAndCaptureEvent(String body) {
        SepayWebhookResponse response = service.handle(body, null, null, API_KEY_HEADER);
        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<PaymentWebhookEvent> captor = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(paymentWebhookEventRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private PaymentTransaction captureTransaction() {
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        return captor.getValue();
    }

    private void stubAttempt(PaymentAttempt attempt) {
        when(paymentAttemptRepository.findTopByTransferContentAndProviderOrderByCreatedAtDesc(
                PAYMENT_CODE, PaymentProvider.SEPAY)).thenReturn(Optional.of(attempt));
    }

    private static Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .paymentCode(PAYMENT_CODE)
                .orderId(UUID.randomUUID())
                .method(PaymentMethod.BANK_TRANSFER)
                .provider(PaymentProvider.SEPAY)
                .status(status)
                .amountRequired(new BigDecimal("100000"))
                .amountPaid(BigDecimal.ZERO)
                .amountRefunded(BigDecimal.ZERO)
                .currency("VND")
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }

    private static PaymentAttempt attempt(Payment payment, PaymentAttemptStatus status, String amountExpected) {
        return PaymentAttempt.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .attemptNo(1)
                .method(PaymentMethod.BANK_TRANSFER)
                .provider(PaymentProvider.SEPAY)
                .status(status)
                .amountExpected(new BigDecimal(amountExpected))
                .currency("VND")
                .transferContent(PAYMENT_CODE)
                .build();
    }

    private static String webhookBody(Long id, String transferType, String amount, String code, String content) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"id\":").append(id);
        json.append(",\"gateway\":\"VCB\"");
        json.append(",\"transactionDate\":\"2026-10-26 09:30:15\"");
        json.append(",\"accountNumber\":\"0000000000\"");
        json.append(",\"transferType\":\"").append(transferType).append('"');
        json.append(",\"transferAmount\":").append(amount);
        json.append(",\"referenceCode\":\"FT").append(id).append('"');
        if (code != null) {
            json.append(",\"code\":\"").append(code).append('"');
        }
        json.append(",\"content\":\"").append(content != null ? content : "Thanh toan " + code).append('"');
        return json.append('}').toString();
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }
}
