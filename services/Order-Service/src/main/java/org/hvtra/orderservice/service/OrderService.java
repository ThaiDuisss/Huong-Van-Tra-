package org.hvtra.orderservice.service;


import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(UUID orderId);
    OrderPaymentInfoResponse getPaymentInfo(UUID orderId);
    OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request);
}