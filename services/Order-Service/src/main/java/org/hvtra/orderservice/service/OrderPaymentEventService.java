package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;

public interface OrderPaymentEventService {

    void handlePaymentEvent(PaymentOrderEventPayload event);

    void handlePaymentSucceeded(PaymentOrderEventPayload event);
}
