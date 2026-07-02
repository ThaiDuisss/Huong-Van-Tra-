package org.hvtra.paymentservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.client.OrderClient;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentAttempt;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.enums.*;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.repository.PaymentAttemptRepository;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.repository.PaymentTransactionRepository;
import org.hvtra.paymentservice.service.PaymentService;
import org.hvtra.paymentservice.service.SepayQrCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private static final String STATUS_UNPAID = "UNPAID";
    private static final String STATUS_WAITING_PAYMENT = "WAITING_PAYMENT";

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderClient orderClient;
    private final SepayQrCodeGenerator sepayQrCodeGenerator;
    private final SepayProperties sepayProperties;

    @Override
    @Transactional
    public PaymentCheckoutResponse checkout(CheckoutRequest request) {
        return switch (request.getPaymentMethod()) {
            case CASH -> payByCash(request);
            case BANK_TRANSFER -> createBankTransferCheckout(request);
        };
    }
    private PaymentCheckoutResponse payByCash(CheckoutRequest request) {
        OrderPaymentInfoResponse orderInfo = getAndValidateOrderPaymentInfo(request);

        BigDecimal amountRequired = orderInfo.getFinalAmount();
        BigDecimal receivedAmount = request.getReceivedAmount();

        if (receivedAmount.compareTo(amountRequired) < 0) {
            throw new IllegalArgumentException("Số tiền khách đưa không đủ để thanh toán");
        }

        if (paymentRepository.existsByOrderIdAndStatusAndIsDeletedFalse(
                request.getOrderId(),
                PaymentStatus.PAID
        )) {
            throw new IllegalArgumentException("Đơn hàng này đã có payment PAID");
        }

        Payment existingPayment = paymentRepository
                .findByOrderIdAndIsDeletedFalse(request.getOrderId())
                .orElse(null);

        if (existingPayment != null && existingPayment.getStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Đơn hàng này đã được thanh toán");
        }

        BigDecimal changeAmount = receivedAmount.subtract(amountRequired);
        LocalDateTime now = LocalDateTime.now();

        Payment payment = existingPayment == null
                ? createBasePayment(orderInfo, request, PaymentMethod.CASH, PaymentProvider.INTERNAL)
                : existingPayment;

        payment.setMethod(PaymentMethod.CASH);
        payment.setProvider(PaymentProvider.INTERNAL);
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmountRequired(amountRequired);
        payment.setAmountPaid(amountRequired);
        payment.setAmountRefunded(defaultZero(payment.getAmountRefunded()));
        payment.setCompletedAt(now);
        payment.setExpiresAt(null);
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
                .currency("VND")
                .paidAt(now)
                .build();

        paymentTransactionRepository.save(transaction);

        /*
         * Chuẩn nhất: ghi PaymentPaidEvent vào outbox ở đây.
         * Order-Service consume event để update order PAID.
         *
         * Không nên Payment-Service gọi ngược Order-Service mark paid trực tiếp,
         * tránh coupling 2 chiều.
         */

        return PaymentCheckoutResponse.builder()
                .paymentId(savedPayment.getId())
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

    private PaymentCheckoutResponse createBankTransferCheckout(CheckoutRequest request) {
        OrderPaymentInfoResponse orderInfo = getAndValidateOrderPaymentInfo(request);

        Payment existingPayment = paymentRepository
                .findByOrderIdAndIsDeletedFalse(request.getOrderId())
                .orElse(null);

        if (existingPayment != null) {
            if (existingPayment.getStatus() == PaymentStatus.PAID) {
                throw new IllegalArgumentException("Đơn hàng này đã được thanh toán");
            }

            if (existingPayment.getMethod() == PaymentMethod.BANK_TRANSFER
                    && existingPayment.getStatus() == PaymentStatus.WAITING_PAYMENT) {
                PaymentAttempt latestAttempt = paymentAttemptRepository
                        .findTopByPaymentIdOrderByAttemptNoDesc(existingPayment.getId())
                        .orElse(null);

                return toBankTransferResponse(existingPayment, latestAttempt);
            }
        }

        BigDecimal amountRequired = orderInfo.getFinalAmount();
        String paymentCode = generateSepayPaymentCode();
        String transferContent = paymentCode;
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(sepayProperties.getPaymentTimeoutMinutes());

        Payment payment = existingPayment == null
                ? createBasePayment(orderInfo, request, PaymentMethod.BANK_TRANSFER, PaymentProvider.SEPAY)
                : existingPayment;

        payment.setPaymentCode(payment.getPaymentCode() == null ? paymentCode : payment.getPaymentCode());
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setStatus(PaymentStatus.WAITING_PAYMENT);
        payment.setAmountRequired(amountRequired);
        payment.setAmountPaid(BigDecimal.ZERO);
        payment.setAmountRefunded(defaultZero(payment.getAmountRefunded()));
        payment.setCurrency("VND");
        payment.setExpiresAt(expiresAt);
        payment.setCompletedAt(null);
        payment.setFailureReason(null);
        payment.setReviewReason(null);

        Payment savedPayment = paymentRepository.save(payment);

        String qrUrl = sepayQrCodeGenerator.generate(transferContent, amountRequired);

        PaymentAttempt attempt = PaymentAttempt.builder()
                .payment(savedPayment)
                .attemptNo(1)
                .method(PaymentMethod.BANK_TRANSFER)
                .provider(PaymentProvider.SEPAY)
                .status(PaymentAttemptStatus.CREATED)
                .amountExpected(amountRequired)
                .currency("VND")
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
            OrderPaymentInfoResponse orderInfo,
            CheckoutRequest request,
            PaymentMethod method,
            PaymentProvider provider
    ) {
        return Payment.builder()
                .paymentCode(generatePaymentCode(method))
                .orderId(request.getOrderId())
                .orderCode(orderInfo.getOrderCode())
                .customerId(orderInfo.getCustomerId())
                .sagaId(orderInfo.getSagaId())
                .method(method)
                .provider(provider)
                .status(PaymentStatus.PENDING)
                .amountRequired(orderInfo.getFinalAmount())
                .amountPaid(BigDecimal.ZERO)
                .amountRefunded(BigDecimal.ZERO)
                .currency("VND")
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
        Payment payment = paymentRepository.findByOrderIdAndIsDeletedFalse(orderId)
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




    private OrderPaymentInfoResponse getAndValidateOrderPaymentInfo(CheckoutRequest request) {
        OrderPaymentInfoResponse orderInfo = orderClient.getPaymentInfo(request.getOrderId());

        if (orderInfo == null) {
            throw new IllegalArgumentException("Không lấy được thông tin đơn hàng");
        }

        if (!STATUS_UNPAID.equals(orderInfo.getPaymentStatus())
                && !STATUS_WAITING_PAYMENT.equals(orderInfo.getPaymentStatus())) {
            throw new IllegalArgumentException("Đơn hàng đã được thanh toán hoặc không hợp lệ");
        }

        if (orderInfo.getFinalAmount() == null
                || orderInfo.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ");
        }

        return orderInfo;
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
