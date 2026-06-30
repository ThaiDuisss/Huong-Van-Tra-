package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CashPaymentRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;

import java.util.UUID;

public interface PaymentService {

    CashPaymentResponse payByCash(CashPaymentRequest request);
    OrderPaymentMethodResponse getPaymentByOrderId(UUID orderId);
}
