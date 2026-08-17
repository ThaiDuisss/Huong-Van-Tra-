package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

public interface OrderCheckoutService {

    OrderResponse createOrder(CreateOrderRequest request);
}
