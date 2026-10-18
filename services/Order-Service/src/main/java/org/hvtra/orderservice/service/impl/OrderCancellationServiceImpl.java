package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.client.PaymentClient;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCancellationService;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationServiceImpl implements OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request) {
        Order order = getExistingOrder(orderId);
        orderValidator.validateCancellation(order);

        OrderStatus oldStatus = order.getStatus();
        if (order.getPaymentStatus() == PaymentStatus.WAITING_PAYMENT) {
            // Đóng phiên chuyển khoản để tiền về sau đó bị gắn cờ kiểm tra thay vì tự hoàn tất đơn đã huỷ.
            try {
                paymentClient.cancelPendingPayment(order.getId());
            } catch (RuntimeException exception) {
                log.warn("Không đóng được phiên thanh toán của đơn {} khi huỷ: {}",
                        order.getOrderCode(), exception.getMessage());
            }
            order.setPaymentStatus(PaymentStatus.FAILED);
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, OrderStatus.CANCELLED, request.getReason(), null);

        return orderResponseAssembler.toResponse(savedOrder);
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }
}
