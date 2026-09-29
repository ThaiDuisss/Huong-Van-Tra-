package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.domain.entity.Order;

public interface OrderPaymentEventService {

    void handlePaymentEvent(PaymentOrderEventPayload event);

    void handlePaymentSucceeded(PaymentOrderEventPayload event);

    void processPaidOrderInventory(Order order, PaymentOrderEventPayload event, boolean immediateConsume);
}
