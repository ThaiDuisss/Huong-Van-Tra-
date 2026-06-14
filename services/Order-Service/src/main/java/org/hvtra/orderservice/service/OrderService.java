package org.hvtra.orderservice.service;


import org.hvtra.orderservice.DTO.request.CreateOrderRequest;
import org.hvtra.orderservice.DTO.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.DTO.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.DTO.response.OrderResponse;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(UUID orderId);
    OrderPaymentInfoResponse getPaymentInfo(UUID orderId);
    OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request);
}