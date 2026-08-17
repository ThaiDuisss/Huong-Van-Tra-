package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderStatusManagementService {

    OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request);
}
