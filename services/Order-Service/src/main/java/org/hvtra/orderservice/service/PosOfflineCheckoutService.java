package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;

/**
 * Đồng bộ đơn đã bán khi POS mất mạng. Mỗi đơn mang {@code clientOrderId} do POS sinh;
 * gọi lại cùng khoá bao nhiêu lần cũng chỉ tạo một đơn và trả về đơn đã có.
 */
public interface PosOfflineCheckoutService {
    OrderResponse checkout(CreateOrderRequest request);
}
