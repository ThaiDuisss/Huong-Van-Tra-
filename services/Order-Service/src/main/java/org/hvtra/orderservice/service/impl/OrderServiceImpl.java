package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.*;
import org.hvtra.orderservice.domain.enums.*;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.*;
import org.hvtra.orderservice.dto.response.*;
import org.hvtra.orderservice.exception.*;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCancellationService;
import org.hvtra.orderservice.service.OrderCheckoutService;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentService;
import org.hvtra.orderservice.service.OrderQueryService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.OrderStatusManagementService;
import org.hvtra.orderservice.service.TodaySalesService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.support.*;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderCustomerServiceImpl orderCustomerServiceImpl;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderPaymentService orderPaymentService;
    private final OrderCheckoutService orderCheckoutService;
    private final OrderQueryService orderQueryService;
    private final TodaySalesService todaySalesService;
    private final OrderDraftService orderDraftService;
    private final OrderCancellationService orderCancellationService;
    private final OrderStatusManagementService orderStatusManagementService;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;

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
    @Transactional
    public void handlePaymentEvent(PaymentOrderEventPayload event) {
        if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
            return;
        }

        String eventType = normalizePaymentEventType(event.getEventType());
        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> handlePaymentSucceededEvent(event);
            case "PAYMENT_FAILED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.PAYMENT_FAILED,
                    "Thanh toán thất bại"
            );
            case "PAYMENT_EXPIRED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.EXPIRED,
                    "Thanh toán hết hạn"
            );
            case "PAYMENT_CANCELLED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.CANCELLED,
                    "Thanh toán đã bị hủy"
            );
            case "PAYMENT_NEED_REVIEW" -> handlePaymentNeedReviewEvent(event);
            case "PAYMENT_WAITING", "PAYMENT_WAITING_PAYMENT" -> handlePaymentWaitingEvent(event);
            default -> log.debug("Bỏ qua payment event chưa được Order-Service xử lý: {}", eventType);
        }
    }

    @Override
    @Transactional
    public void handlePaymentSucceeded(PaymentOrderEventPayload event) {
        handlePaymentEvent(event);
    }

    private void handlePaymentSucceededEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            orderPaymentSagaService.completePaymentSaga(order, event, paymentCausationId(event));
            return;
        }

        BigDecimal amountPaid = orderAmountCalculator.defaultZero(event.getAmountPaid());
        orderValidator.validatePaymentAmount(order, amountPaid);

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                "Thanh toán chuyển khoản thành công, mã thanh toán: " + savedOrder.getPaymentCode(),
                null
        );

        UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, paymentCausationId(event));
        orderCustomerServiceImpl.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(savedOrder, paymentCausationId(event), sagaId.toString());
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder,
                event,
                oldStatus,
                OrderStatus.COMPLETED,
                sagaId.toString()
        );
    }

    private void handlePaymentFailedEvent(PaymentOrderEventPayload event, OrderStatus targetOrderStatus, String defaultReason) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(targetOrderStatus);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        String reason = firstNonBlank(event.getFailureReason(), defaultReason);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, targetOrderStatus, reason, null);
        orderPaymentSagaService.failPaymentSaga(savedOrder, event, reason);

        if (targetOrderStatus == OrderStatus.CANCELLED) {
            orderOutboxPublisher.publishOrderCancelledNotification(
                    savedOrder,
                    event,
                    oldStatus,
                    targetOrderStatus,
                    event.getSagaId(),
                    reason
            );
        } else {
            orderOutboxPublisher.publishOrderPaymentFailedNotification(
                    savedOrder,
                    event,
                    oldStatus,
                    targetOrderStatus,
                    event.getSagaId(),
                    reason
            );
        }
    }

    private void handlePaymentNeedReviewEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        String reason = firstNonBlank(event.getFailureReason(), "Thanh toán cần kiểm tra thủ công");
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            orderStatusHistoryWriter.write(savedOrder, oldStatus, OrderStatus.PENDING_PAYMENT, reason, null);
        }
        orderPaymentSagaService.markPaymentNeedReview(savedOrder, event, reason);
    }

    private void handlePaymentWaitingEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            orderStatusHistoryWriter.write(
                    savedOrder,
                    oldStatus,
                    OrderStatus.PENDING_PAYMENT,
                    "Đang chờ thanh toán",
                    null
            );
        }
        orderPaymentSagaService.markPaymentWaiting(savedOrder, event, "Đang chờ payment event thành công");
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

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private String paymentCausationId(PaymentOrderEventPayload event) {
        if (event == null) {
            return null;
        }
        return firstNonBlank(
                event.getEventId(),
                firstNonBlank(event.getEventKey(), firstNonBlank(event.getPaymentCode(), event.getPaymentId()))
        );
    }

    private String normalizePaymentEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }

        String normalized = eventType.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace('.', '_');

        if (normalized.endsWith("PAYMENT_SUCCEEDED") || normalized.endsWith("SUCCEEDED")) {
            return "PAYMENT_SUCCEEDED";
        }
        if (normalized.endsWith("PAYMENT_FAILED") || normalized.endsWith("FAILED")) {
            return "PAYMENT_FAILED";
        }
        if (normalized.endsWith("PAYMENT_EXPIRED") || normalized.endsWith("EXPIRED")) {
            return "PAYMENT_EXPIRED";
        }
        if (normalized.endsWith("PAYMENT_CANCELLED") || normalized.endsWith("PAYMENT_CANCELED")
                || normalized.endsWith("CANCELLED") || normalized.endsWith("CANCELED")) {
            return "PAYMENT_CANCELLED";
        }
        if (normalized.endsWith("PAYMENT_NEED_REVIEW") || normalized.endsWith("NEED_REVIEW")) {
            return "PAYMENT_NEED_REVIEW";
        }
        if (normalized.endsWith("PAYMENT_WAITING_PAYMENT") || normalized.endsWith("WAITING_PAYMENT")
                || normalized.endsWith("PAYMENT_WAITING") || normalized.endsWith("WAITING")) {
            return "PAYMENT_WAITING";
        }

        return normalized;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

}
