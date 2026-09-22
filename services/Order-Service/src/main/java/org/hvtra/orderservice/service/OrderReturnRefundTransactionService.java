package org.hvtra.orderservice.service;

import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;

import java.util.UUID;

public interface OrderReturnRefundTransactionService {

    OrderReturnRefundResponse applyRefundSuccess(
            UUID returnId,
            PaymentClient.PaymentRefundResponse paymentRefund,
            UUID actorUserId
    );
}
