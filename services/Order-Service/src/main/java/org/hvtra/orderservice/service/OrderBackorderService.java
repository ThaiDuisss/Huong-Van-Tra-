package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.response.BackorderAvailabilityResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** Đơn chờ hàng: đã thu tiền (đủ hoặc cọc) nhưng giao hàng và trừ tồn sau khi có hàng. */
public interface OrderBackorderService {

    Page<OrderResponse> getWaitingOrders(String keyword, int page, int size);

    BackorderAvailabilityResponse checkAvailability(UUID orderId);

    OrderResponse fulfill(UUID orderId, FulfillBackorderRequest request);
}
