package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderCancellationService {

    OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request);
}
