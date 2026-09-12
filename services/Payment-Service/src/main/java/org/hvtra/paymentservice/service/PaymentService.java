package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CheckoutRequest;
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

}
