package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.*;
import org.hvtra.orderservice.dto.response.*;
import org.hvtra.orderservice.service.OrderCancellationService;
import org.hvtra.orderservice.service.OrderCheckoutService;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentService;
import org.hvtra.orderservice.service.OrderQueryService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.OrderStatusManagementService;
import org.hvtra.orderservice.service.TodaySalesService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderPaymentService orderPaymentService;
    private final OrderPaymentEventService orderPaymentEventService;
    private final OrderCheckoutService orderCheckoutService;
    private final OrderQueryService orderQueryService;
    private final TodaySalesService todaySalesService;
    private final OrderDraftService orderDraftService;
    private final OrderCancellationService orderCancellationService;
    private final OrderStatusManagementService orderStatusManagementService;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        return orderCheckoutService.createOrder(request);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        return orderQueryService.getOrderById(orderId);
    }

    @Override
    public OrderPaymentInfoResponse getPaymentInfo(UUID orderId) {
        return orderQueryService.getPaymentInfo(orderId);
    }

    @Override
    public OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        return orderPaymentService.markOrderPaid(orderId, request);
    }

    @Override
    public void handlePaymentEvent(PaymentOrderEventPayload event) {
        orderPaymentEventService.handlePaymentEvent(event);
    }

    @Override
    public void handlePaymentSucceeded(PaymentOrderEventPayload event) {
        orderPaymentEventService.handlePaymentSucceeded(event);
    }

    @Override
    public OrderResponse addDraftItem(AddDraftOrderItemRequest request) {
        return orderDraftService.addDraftItem(request);
    }

    @Override
    public OrderResponse updateDraftItemQuantity(UUID orderId, UUID itemId, UpdateDraftOrderItemQuantityRequest request) {
        return orderDraftService.updateDraftItemQuantity(orderId, itemId, request);
    }

    @Override
    public OrderResponse removeDraftItem(UUID orderId, UUID itemId) {
        return orderDraftService.removeDraftItem(orderId, itemId);
    }

    @Override
    public OrderResponse cancelDraftOrder(UUID orderId) {
        return orderDraftService.cancelDraftOrder(orderId);
    }

    @Override
    public OrderResponse checkoutDraftOrder(UUID orderId, CheckoutDraftOrderRequest request) {
        return orderDraftService.checkoutDraftOrder(orderId, request);
    }

    @Override
    public Page<OrderSummaryResponse> getOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return orderQueryService.getOrders(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate,
                sort,
                page,
                size
        );
    }

    @Override
    public OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate) {
        return orderQueryService.getOrderStats(fromDate, toDate);
    }

    @Override
    public Page<TodaySalesItemResponse> getTodaySales(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethod,
            int page,
            int size
    ) {
        return todaySalesService.getTodaySales(status, paymentStatus, paymentMethod, page, size);
    }

    @Override
    public TodaySalesSummaryResponse getTodaySalesSummary() {
        return todaySalesService.getTodaySalesSummary();
    }

    @Override
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        return orderStatusManagementService.updateOrderStatus(orderId, request);
    }

    @Override
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request) {
        return orderCancellationService.cancelOrder(orderId, request);
    }

}
