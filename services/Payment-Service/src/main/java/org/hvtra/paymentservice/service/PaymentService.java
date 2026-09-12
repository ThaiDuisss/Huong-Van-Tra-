package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.request.RefundPaymentRequest;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.dto.response.PaymentRefundResponse;

import java.util.UUID;

public interface PaymentService {

    PaymentCheckoutResponse checkout(CheckoutRequest request);

    PaymentRefundResponse refundOrderReturn(UUID orderId, RefundPaymentRequest request);

    OrderPaymentMethodResponse getPaymentByOrderId(UUID orderId);

}
