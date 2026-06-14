package org.hvtra.orderservice.domain.service;


import org.hvtra.orderservice.domain.DTO.request.*;
import org.hvtra.orderservice.domain.DTO.response.*;

import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(UUID orderId);
    OrderPaymentInfoResponse getPaymentInfo(UUID orderId);
    OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request);
}