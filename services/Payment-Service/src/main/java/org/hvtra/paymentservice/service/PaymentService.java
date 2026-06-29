package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;

public interface PaymentService {

    CashPaymentResponse payByCash(CheckoutRequest request);
}