package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;

import java.util.UUID;

public interface PaymentService {

    PaymentCheckoutResponse checkout(CheckoutRequest request);

    OrderPaymentMethodResponse getPaymentByOrderId(UUID orderId);

}
