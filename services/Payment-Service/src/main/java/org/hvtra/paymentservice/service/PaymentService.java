package org.hvtra.paymentservice.service;

import org.hvtra.paymentservice.domain.dto.request.CashPaymentRequest;
import org.hvtra.paymentservice.domain.dto.response.CashPaymentResponse;

public interface PaymentService {

    CashPaymentResponse payByCash(CashPaymentRequest request);
}