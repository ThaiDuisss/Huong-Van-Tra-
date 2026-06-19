package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.dto.request.CashPaymentRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;

public interface PaymentService {

    CashPaymentResponse payByCash(CashPaymentRequest request);
}