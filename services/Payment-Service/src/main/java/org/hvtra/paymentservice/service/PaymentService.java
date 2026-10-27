package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.request.ManualPaymentConfirmRequest;
import org.hvtra.paymentservice.dto.request.RefundPaymentRequest;
import org.hvtra.paymentservice.dto.request.RefundSummaryLookupRequest;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentCheckoutResponse checkout(CheckoutRequest request);

    PaymentRefundResponse refundOrderReturn(UUID orderId, RefundPaymentRequest request);

    List<PaymentRefundSummaryResponse> lookupRefundSummaries(RefundSummaryLookupRequest request);

    OrderPaymentMethodResponse getPaymentByOrderId(UUID orderId);

    /** Thu ngân huỷ phiên chuyển khoản đang chờ tiền: payment và attempt chuyển CANCELLED, không phát event. */
    OrderPaymentMethodResponse cancelPendingPayment(UUID orderId);

    /** Đối soát tay: giao dịch NEED_REVIEW / chờ / hết hạn được ghi PAID. */
    OrderPaymentMethodResponse confirmManualPayment(UUID orderId, ManualPaymentConfirmRequest request);

    /** QR quá 15 phút chưa có tiền: payment EXPIRED + event PAYMENT_EXPIRED để Order-Service trả đơn về nháp. */
    int expireOverduePayments();

}
