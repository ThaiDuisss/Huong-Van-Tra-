package org.hvtra.paymentservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.enums.PaymentAttemptStatus;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionType;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.request.RefundPaymentRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundResponse;
import org.hvtra.paymentservice.repository.PaymentAttemptRepository;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.repository.PaymentTransactionRepository;
import org.hvtra.paymentservice.service.PaymentService;
import org.hvtra.paymentservice.service.SepayQrCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String CURRENCY_VND = "VND";
    private static final EnumSet<PaymentStatus> REFUNDABLE_PAYMENT_STATUSES =
            EnumSet.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED);

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SepayQrCodeGenerator sepayQrCodeGenerator;
    private final SepayProperties sepayProperties;

    @Override
    @Transactional
    public PaymentCheckoutResponse checkout(CheckoutRequest request) {
        validatePaymentCheckoutRequest(request);

        return switch (request.getPaymentMethod()) {
            case CASH -> payByCash(request);
            case BANK_TRANSFER -> createBankTransferCheckout(request);
        };
    }

    @Override
    @Transactional
    public PaymentRefundResponse refundOrderReturn(UUID orderId, RefundPaymentRequest request) {
        validateRefundRequest(orderId, request);

        String refundReference = request.getReturnId().toString();
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found"));

        PaymentTransaction existingTransaction = paymentTransactionRepository
                .findByRefundReference(refundReference)
                .orElse(null);
        if (existingTransaction != null) {
            if (existingTransaction.getPayment() == null
                    || !payment.getId().equals(existingTransaction.getPayment().getId())) {
                throw new BusinessException("REFUND_ALREADY_EXISTS", "Refund has already been created for this return");
            }
            if (existingTransaction.getStatus() == PaymentTransactionStatus.SUCCEEDED) {
                return toRefundResponse(payment, existingTransaction);
            }
            throw new BusinessException("REFUND_ALREADY_EXISTS", "Refund has already been created for this return");
        }

        if (payment.getMethod() != PaymentMethod.CASH) {
            throw new BusinessException("UNSUPPORTED_REFUND_METHOD", "Refund method is not supported yet");
        }
        if (!REFUNDABLE_PAYMENT_STATUSES.contains(payment.getStatus())) {
            throw new BusinessException("PAYMENT_NOT_REFUNDABLE", "Payment is not eligible for refund");
        }

        BigDecimal refundAmount = normalizeMoney(request.getAmount());
        BigDecimal amountPaid = defaultZero(payment.getAmountPaid());
        BigDecimal amountRefunded = defaultZero(payment.getAmountRefunded());
        BigDecimal remainingRefundable = amountPaid.subtract(amountRefunded);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_REFUND_AMOUNT", "Refund amount must be greater than 0");
        }
        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_REMAINING", "Refund amount exceeds remaining refundable amount");
        }

        LocalDateTime refundedAt = LocalDateTime.now();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .transactionType(PaymentTransactionType.REFUND)
                .method(PaymentMethod.CASH)
                .provider(PaymentProvider.INTERNAL)
                .status(PaymentTransactionStatus.SUCCEEDED)
                .amount(refundAmount)
                .currency(CURRENCY_VND)
                .referenceCode("RETURN-" + request.getReturnId())
                .refundReference(refundReference)
                .paidAt(refundedAt)
                .build();

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        BigDecimal totalRefunded = amountRefunded.add(refundAmount);
        if (totalRefunded.compareTo(amountPaid) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS_REMAINING", "Refund amount exceeds remaining refundable amount");
        }

        payment.setAmountRefunded(totalRefunded);
        payment.setStatus(totalRefunded.compareTo(amountPaid) < 0
                ? PaymentStatus.PARTIALLY_REFUNDED
                : PaymentStatus.REFUNDED);
        Payment savedPayment = paymentRepository.save(payment);

        return toRefundResponse(savedPayment, savedTransaction);
    }

    /**
     * CASH là luồng đồng bộ: Order-Service gọi Feign sang đây, nhận response PAID
     * rồi tự cập nhật Order.
     * Vì vậy không publish PAYMENT_SUCCEEDED ở đây để tránh Order nhận trùng trạng
     * thái qua 2 đường.
     */
    private PaymentCheckoutResponse payByCash(CheckoutRequest request) {
        BigDecimal amountRequired = normalizeMoney(request.getFinalAmount());
        BigDecimal receivedAmount = normalizeMoney(request.getReceivedAmount());

        Payment existingPayment = paymentRepository
                .findByOrderId(request.getOrderId())
                .orElse(null);

        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Đơn hàng này đã được thanh toán");
        }

        BigDecimal changeAmount = receivedAmount.subtract(amountRequired);
        LocalDateTime now = LocalDateTime.now();

        Payment payment = existingPayment == null
                ? createBasePayment(request, PaymentMethod.CASH, PaymentProvider.INTERNAL)
                : existingPayment;

        payment.setPaymentCode(resolvePaymentCode(payment, PaymentMethod.CASH));
        payment.setMethod(PaymentMethod.CASH);
        payment.setProvider(PaymentProvider.INTERNAL);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmountRequired(amountRequired);
        payment.setAmountPaid(amountRequired);
        payment.setAmountRefunded(defaultZero(payment.getAmountRefunded()));
        payment.setCurrency(CURRENCY_VND);
        payment.setExpiresAt(null);
        payment.setCompletedAt(now);
        payment.setCancelledAt(null);
        payment.setFailureReason(null);
        payment.setReviewReason(null);

        Payment savedPayment = paymentRepository.save(payment);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(savedPayment)
                .transactionType(PaymentTransactionType.PAYMENT)
                .method(PaymentMethod.CASH)
                .provider(PaymentProvider.INTERNAL)
                .status(PaymentTransactionStatus.MATCHED)
                .amount(amountRequired)
                .currency(CURRENCY_VND)
                .paidAt(now)
                .build();

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        return PaymentCheckoutResponse.builder()
                .paymentId(savedPayment.getId())
                .attemptId(savedTransaction.getAttempt() == null ? null : savedTransaction.getAttempt().getId())
                .paymentCode(savedPayment.getPaymentCode())
                .orderId(savedPayment.getOrderId())
                .orderCode(savedPayment.getOrderCode())
                .amountRequired(savedPayment.getAmountRequired())
                .amountPaid(savedPayment.getAmountPaid())
                .receivedAmount(receivedAmount)
                .changeAmount(changeAmount)
                .status(savedPayment.getStatus().name())
                .method(savedPayment.getMethod().name())
                .methodLabel(toPaymentMethodLabel(savedPayment.getMethod()))
                .provider(savedPayment.getProvider().name())
                .build();
    }

    /**
     * BANK_TRANSFER là luồng bất đồng bộ: trả QR ngay cho Order/FE, còn kết quả
     * thật sẽ được SePay webhook xác nhận.
     * Order-Service nên dùng response này để set paymentStatus = WAITING_PAYMENT.
     * Khi webhook success, Payment-Service mới ghi outbox để bắn event sang
     * Order-Service.
     */
    private PaymentCheckoutResponse createBankTransferCheckout(CheckoutRequest request) {
        BigDecimal amountRequired = normalizeMoney(request.getFinalAmount());
        LocalDateTime now = LocalDateTime.now();

        Payment existingPayment = paymentRepository
                .findByOrderId(request.getOrderId())
                .orElse(null);

        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Đơn hàng này đã được thanh toán");
        }

        PaymentAttempt latestAttempt = null;

        if (existingPayment != null) {
            latestAttempt = paymentAttemptRepository
                    .findTopByPaymentIdOrderByAttemptNoDesc(existingPayment.getId())
                    .orElse(null);

            boolean reusableWaitingPayment = existingPayment.getMethod() == PaymentMethod.BANK_TRANSFER
                    && existingPayment.getStatus() == PaymentStatus.WAITING_PAYMENT
                    && latestAttempt != null
                    && latestAttempt.getStatus() == PaymentAttemptStatus.WAITING
                    && !isExpired(latestAttempt.getExpiresAt(), now)
                    && sameAmount(latestAttempt.getAmountExpected(), amountRequired);

            if (reusableWaitingPayment) {
                return toBankTransferResponse(existingPayment, latestAttempt);
            }
        }

        Payment payment = existingPayment == null
                ? createBasePayment(request, PaymentMethod.BANK_TRANSFER, PaymentProvider.SEPAY)
                : existingPayment;

        payment.setPaymentCode(resolvePaymentCode(payment, PaymentMethod.BANK_TRANSFER));

        LocalDateTime expiresAt = now.plusMinutes(sepayProperties.getPaymentTimeoutMinutes());

        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setStatus(PaymentStatus.WAITING_PAYMENT);
        payment.setAmountRequired(amountRequired);
        payment.setAmountPaid(BigDecimal.ZERO);
        payment.setAmountRefunded(defaultZero(payment.getAmountRefunded()));
        payment.setCurrency(CURRENCY_VND);
        payment.setExpiresAt(expiresAt);
        payment.setCompletedAt(null);
        payment.setCancelledAt(null);
        payment.setFailureReason(null);
        payment.setReviewReason(null);

        Payment savedPayment = paymentRepository.save(payment);

        String transferContent = savedPayment.getPaymentCode();
        String qrUrl = sepayQrCodeGenerator.generate(transferContent, amountRequired);

        int nextAttemptNo = latestAttempt == null
                ? 1
                : latestAttempt.getAttemptNo() + 1;

        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(savedPayment)
                .attemptNo(nextAttemptNo)
                .method(PaymentMethod.BANK_TRANSFER)
                .provider(PaymentProvider.SEPAY)
                .status(PaymentAttemptStatus.WAITING)
                .amountExpected(amountRequired)
                .currency(CURRENCY_VND)
                .transferContent(transferContent)
                .qrUrl(qrUrl)
                .bankCode(sepayProperties.getBankCode())
                .bankAccountNumber(sepayProperties.getAccountNumber())
                .bankAccountName(sepayProperties.getAccountName())
                .expiresAt(expiresAt)
                .build();

        PaymentAttempt savedAttempt = paymentAttemptRepository.save(attempt);

        return toBankTransferResponse(savedPayment, savedAttempt);
    }

    private Payment createBasePayment(
            CheckoutRequest request,
            PaymentMethod method,
            PaymentProvider provider) {
        return Payment.builder()
                .paymentCode(generatePaymentCode(method))
                .orderId(request.getOrderId())
                .orderCode(request.getOrderCode())
                .customerId(request.getCustomerId())
                .sagaId(request.getSagaId())
                .method(method)
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amountRequired(normalizeMoney(request.getFinalAmount()))
                .amountPaid(BigDecimal.ZERO)
                .amountRefunded(BigDecimal.ZERO)
                .currency(CURRENCY_VND)
                .build();
    }

    private PaymentCheckoutResponse toBankTransferResponse(Payment payment, PaymentAttempt attempt) {
        return PaymentCheckoutResponse.builder()
                .paymentId(payment.getId())
                .attemptId(attempt == null ? null : attempt.getId())
                .paymentCode(payment.getPaymentCode())
                .orderId(payment.getOrderId())
                .orderCode(payment.getOrderCode())
                .amountRequired(payment.getAmountRequired())
                .amountPaid(payment.getAmountPaid())
                .status(payment.getStatus().name())
                .method(payment.getMethod().name())
                .methodLabel(toPaymentMethodLabel(payment.getMethod()))
                .provider(payment.getProvider().name())
                .transferContent(attempt == null ? null : attempt.getTransferContent())
                .qrUrl(attempt == null ? null : attempt.getQrUrl())
                .bankCode(attempt == null ? null : attempt.getBankCode())
                .bankAccountNumber(attempt == null ? null : attempt.getBankAccountNumber())
                .bankAccountName(attempt == null ? null : attempt.getBankAccountName())
                .expiresAt(attempt == null ? payment.getExpiresAt() : attempt.getExpiresAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentMethodResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElse(null);

        if (payment == null) {
            return null;
        }

        return OrderPaymentMethodResponse.builder()
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .orderId(payment.getOrderId())
                .amountRequired(payment.getAmountRequired())
                .amountPaid(payment.getAmountPaid())
                .status(payment.getStatus().name())
                .method(payment.getMethod().name())
                .methodLabel(toPaymentMethodLabel(payment.getMethod()))
                .build();
    }

    private void validatePaymentCheckoutRequest(CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request không được để trống");
        }

        if (request.getOrderId() == null) {
            throw new IllegalArgumentException("orderId không được để trống");
        }

        if (request.getOrderCode() == null || request.getOrderCode().isBlank()) {
            throw new IllegalArgumentException("orderCode không được để trống");
        }

        if (request.getSagaId() == null) {
            throw new IllegalArgumentException("sagaId không được để trống");
        }

        if (request.getFinalAmount() == null
                || request.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ");
        }

        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Phương thức thanh toán không được để trống");
        }

        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            if (request.getReceivedAmount() == null) {
                throw new IllegalArgumentException("Số tiền nhận bắt buộc khi thanh toán tiền mặt");
            }

            if (request.getReceivedAmount().compareTo(request.getFinalAmount()) < 0) {
                throw new IllegalArgumentException("Số tiền nhận không đủ");
            }
        }

        if (paymentRepository.existsByOrderIdAndStatus(
                request.getOrderId(),
                PaymentStatus.PAID)) {
            throw new IllegalArgumentException("Đơn hàng đã được thanh toán");
        }
    }

    private void validateRefundRequest(UUID orderId, RefundPaymentRequest request) {
        if (orderId == null) {
            throw new BusinessException("INVALID_REFUND_REQUEST", "orderId không được để trống");
        }
        if (request == null) {
            throw new BusinessException("INVALID_REFUND_REQUEST", "Request không được để trống");
        }
        if (request.getReturnId() == null) {
            throw new BusinessException("INVALID_REFUND_REQUEST", "returnId không được để trống");
        }
        if (request.getAmount() == null) {
            throw new BusinessException("INVALID_REFUND_REQUEST", "amount không được để trống");
        }
    }

    private PaymentRefundResponse toRefundResponse(Payment payment, PaymentTransaction transaction) {
        return PaymentRefundResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .refundTransactionId(transaction.getId())
                .returnId(parseUuid(transaction.getRefundReference()))
                .refundAmount(transaction.getAmount())
                .totalAmountRefunded(payment.getAmountRefunded())
                .paymentStatus(payment.getStatus().name())
                .method(payment.getMethod().name())
                .refundedAt(transaction.getPaidAt())
                .build();
    }

    private UUID parseUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private boolean isExpired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    private boolean sameAmount(BigDecimal currentAmount, BigDecimal requestAmount) {
        if (currentAmount == null || requestAmount == null) {
            return false;
        }

        return normalizeMoney(currentAmount).compareTo(normalizeMoney(requestAmount)) == 0;
    }

    private String resolvePaymentCode(Payment payment, PaymentMethod method) {
        if (payment.getPaymentCode() != null && !payment.getPaymentCode().isBlank()) {
            return payment.getPaymentCode();
        }
        return generatePaymentCode(method);
    }

    private String generatePaymentCode(PaymentMethod method) {
        if (method == PaymentMethod.BANK_TRANSFER) {
            return generateSepayPaymentCode();
        }

        return "PAY" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String generateSepayPaymentCode() {
        return "HVTRA" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyMMddHHmmssSSS"));
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toPaymentMethodLabel(PaymentMethod method) {
        if (method == null) {
            return null;
        }

        return switch (method) {
            case CASH -> "Tiền mặt";
            case BANK_TRANSFER -> "Chuyển khoản";
        };
    }
}
