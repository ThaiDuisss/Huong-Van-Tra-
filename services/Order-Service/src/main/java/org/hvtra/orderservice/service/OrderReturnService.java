package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;

import java.util.List;
import java.util.UUID;

public interface OrderReturnService {
    OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request);

    List<OrderReturnResponse> getReturnsByOrder(UUID orderId);

    OrderReturnResponse getReturnById(UUID returnId);
}
