package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentEventServiceImpl implements OrderPaymentEventService {

    private final OrderRepository orderRepository;
    private final OrderCustomerService orderCustomerService;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;

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
        orderCustomerService.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(
                savedOrder,
                paymentCausationId(event),
                sagaId.toString()
        );
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder,
                event,
                oldStatus,
                OrderStatus.COMPLETED,
                sagaId.toString()
        );
    }

    private void handlePaymentFailedEvent(
            PaymentOrderEventPayload event,
            OrderStatus targetOrderStatus,
            String defaultReason
    ) {
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
