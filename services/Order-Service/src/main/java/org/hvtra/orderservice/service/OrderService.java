package org.hvtra.orderservice.service;


import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(UUID orderId);
    OrderPaymentInfoResponse getPaymentInfo(UUID orderId);
    OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request);
    Page<OrderSummaryResponse> getOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );
    Page<TodaySalesItemResponse> getTodaySales(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethod,
            int page,
            int size
    );
    TodaySalesSummaryResponse getTodaySalesSummary();
    OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate);
    OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request);
    OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request);
}
