package org.hvtra.paymentservice.service.impl;

import org.hvtra.commonservice.exception.BusinessException;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.domain.entity.Payment;
import org.hvtra.paymentservice.domain.entity.PaymentTransaction;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionType;
import org.hvtra.paymentservice.dto.request.RefundPaymentRequest;
import org.hvtra.paymentservice.dto.response.PaymentRefundResponse;
import org.hvtra.paymentservice.repository.PaymentAttemptRepository;
import org.hvtra.paymentservice.repository.PaymentRepository;
import org.hvtra.paymentservice.repository.PaymentTransactionRepository;
import org.hvtra.paymentservice.service.SepayQrCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private SepayQrCodeGenerator sepayQrCodeGenerator;

    @Mock
    private SepayProperties sepayProperties;

    private PaymentServiceImpl service;
    private UUID orderId;
    private UUID returnId;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(
                paymentRepository,
                paymentAttemptRepository,
                paymentTransactionRepository,
                sepayQrCodeGenerator,
                sepayProperties
        );
        orderId = UUID.randomUUID();
        returnId = UUID.randomUUID();
    }

    @Test
    void refundOrderReturnCreatesCashRefundAndMarksPartiallyRefunded() {
        Payment payment = payment(PaymentMethod.CASH, PaymentStatus.PAID, "200000", "0");
        RefundPaymentRequest request = refundRequest("80000");
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByRefundReference(returnId.toString())).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any())).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentRefundResponse response = service.refundOrderReturn(orderId, request);

        assertEquals(new BigDecimal("80000"), response.getRefundAmount());
        assertEquals(new BigDecimal("80000"), response.getTotalAmountRefunded());
        assertEquals("PARTIALLY_REFUNDED", response.getPaymentStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payment.getStatus());
        assertEquals(new BigDecimal("80000"), payment.getAmountRefunded());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        PaymentTransaction savedTransaction = captor.getValue();
        assertEquals(PaymentTransactionType.REFUND, savedTransaction.getTransactionType());
        assertEquals(PaymentTransactionStatus.SUCCEEDED, savedTransaction.getStatus());
        assertEquals(returnId.toString(), savedTransaction.getRefundReference());
    }

    @Test
    void refundOrderReturnReturnsExistingSucceededRefundForSameReturnId() {
        Payment payment = payment(PaymentMethod.CASH, PaymentStatus.PARTIALLY_REFUNDED, "200000", "80000");
        PaymentTransaction existingTransaction = PaymentTransaction.builder()
                .payment(payment)
                .transactionType(PaymentTransactionType.REFUND)
                .method(PaymentMethod.CASH)
                .provider(PaymentProvider.INTERNAL)
                .status(PaymentTransactionStatus.SUCCEEDED)
                .amount(new BigDecimal("80000"))
                .refundReference(returnId.toString())
                .build();
        existingTransaction.setId(UUID.randomUUID());
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByRefundReference(returnId.toString()))
                .thenReturn(Optional.of(existingTransaction));

        PaymentRefundResponse response = service.refundOrderReturn(orderId, refundRequest("80000"));

        assertSame(existingTransaction.getId(), response.getRefundTransactionId());
        assertEquals(new BigDecimal("80000"), response.getRefundAmount());
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundOrderReturnRejectsBankTransfer() {
        Payment payment = payment(PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, "200000", "0");
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByRefundReference(returnId.toString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.refundOrderReturn(orderId, refundRequest("80000"))
        );

        assertEquals("Refund method is not supported yet", exception.getMessage());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void refundOrderReturnRejectsAmountAboveRemainingRefundable() {
        Payment payment = payment(PaymentMethod.CASH, PaymentStatus.PARTIALLY_REFUNDED, "200000", "150000");
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByRefundReference(returnId.toString())).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.refundOrderReturn(orderId, refundRequest("60000"))
        );

        assertEquals("Refund amount exceeds remaining refundable amount", exception.getMessage());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void lookupRefundSummariesReturnsNotFoundWhenPaymentDoesNotExist() {
        org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest request =
                new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest(
                        java.util.List.of(new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest.RefundSummaryLookupItem(returnId, orderId))
                );
        when(paymentRepository.findByOrderIdInAndIsDeletedFalse(java.util.List.of(orderId))).thenReturn(java.util.List.of());
        when(paymentTransactionRepository.findRefundTransactions(
                java.util.List.of(returnId.toString()),
                PaymentTransactionType.REFUND,
                PaymentTransactionStatus.SUCCEEDED
        )).thenReturn(java.util.List.of());

        java.util.List<org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse> responses =
                service.lookupRefundSummaries(request);

        assertEquals(1, responses.size());
        org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse summary = responses.get(0);
        assertEquals("NOT_FOUND", summary.getRefundStatus());
        org.junit.jupiter.api.Assertions.assertNull(summary.getPaymentId());
        org.junit.jupiter.api.Assertions.assertNull(summary.getAmountPaid());
    }

    @Test
    void lookupRefundSummariesDistinguishesPartiallyRefundedVsFullRefunded() {
        Payment partialPayment = payment(PaymentMethod.CASH, PaymentStatus.PARTIALLY_REFUNDED, "500000", "200000");
        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(partialPayment)
                .transactionType(PaymentTransactionType.REFUND)
                .method(PaymentMethod.CASH)
                .provider(PaymentProvider.INTERNAL)
                .status(PaymentTransactionStatus.SUCCEEDED)
                .amount(new BigDecimal("200000"))
                .refundReference(returnId.toString())
                .build();
        transaction.setId(UUID.randomUUID());

        org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest request =
                new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest(
                        java.util.List.of(new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest.RefundSummaryLookupItem(returnId, orderId))
                );
        when(paymentRepository.findByOrderIdInAndIsDeletedFalse(java.util.List.of(orderId))).thenReturn(java.util.List.of(partialPayment));
        when(paymentTransactionRepository.findRefundTransactions(
                java.util.List.of(returnId.toString()),
                PaymentTransactionType.REFUND,
                PaymentTransactionStatus.SUCCEEDED
        )).thenReturn(java.util.List.of(transaction));

        java.util.List<org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse> responses =
                service.lookupRefundSummaries(request);

        assertEquals(1, responses.size());
        org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse summary = responses.get(0);
        assertEquals("PARTIALLY_REFUNDED", summary.getRefundStatus());
        assertEquals(new BigDecimal("500000"), summary.getAmountPaid());
        assertEquals(new BigDecimal("200000"), summary.getTotalAmountRefunded());
        assertEquals(new BigDecimal("200000"), summary.getRefundAmount());
    }

    @Test
    void lookupRefundSummariesReturnsPendingWhenPaymentPaidAndNoRefundTransaction() {
        Payment paidPayment = payment(PaymentMethod.CASH, PaymentStatus.PAID, "500000", "0");

        org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest request =
                new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest(
                        java.util.List.of(new org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest.RefundSummaryLookupItem(returnId, orderId))
                );
        when(paymentRepository.findByOrderIdInAndIsDeletedFalse(java.util.List.of(orderId))).thenReturn(java.util.List.of(paidPayment));
        when(paymentTransactionRepository.findRefundTransactions(
                java.util.List.of(returnId.toString()),
                PaymentTransactionType.REFUND,
                PaymentTransactionStatus.SUCCEEDED
        )).thenReturn(java.util.List.of());

        java.util.List<org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse> responses =
                service.lookupRefundSummaries(request);

        assertEquals(1, responses.size());
        org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse summary = responses.get(0);
        assertEquals("PENDING", summary.getRefundStatus());
        assertEquals("PAID", summary.getPaymentStatus());
        assertEquals(new BigDecimal("500000"), summary.getAmountPaid());
        org.junit.jupiter.api.Assertions.assertNull(summary.getRefundTransactionId());
    }

    private RefundPaymentRequest refundRequest(String amount) {
        RefundPaymentRequest request = new RefundPaymentRequest();
        request.setReturnId(returnId);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private Payment payment(
            PaymentMethod method,
            PaymentStatus status,
            String amountPaid,
            String amountRefunded
    ) {
        Payment value = Payment.builder()
                .paymentCode("PAY-001")
                .orderId(orderId)
                .sagaId(UUID.randomUUID())
                .method(method)
                .provider(method == PaymentMethod.CASH ? PaymentProvider.INTERNAL : PaymentProvider.SEPAY)
                .status(status)
                .amountRequired(new BigDecimal(amountPaid))
                .amountPaid(new BigDecimal(amountPaid))
                .amountRefunded(new BigDecimal(amountRefunded))
                .currency("VND")
                .build();
        value.setId(UUID.randomUUID());
        value.setIsDeleted(false);
        return value;
    }
}
