package org.hvtra.orderservice.service;

import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderQueryService {

    OrderResponse getOrderById(UUID orderId);

    List<OrderResponse> getOrdersByIdsForExport(List<UUID> orderIds);

    OrderPaymentInfoResponse getPaymentInfo(UUID orderId);

    Page<OrderSummaryResponse> getOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    );

    OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate);
}
