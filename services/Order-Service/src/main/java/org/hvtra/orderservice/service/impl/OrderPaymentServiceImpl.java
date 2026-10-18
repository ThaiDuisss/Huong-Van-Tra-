package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.client.PaymentClient;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderCustomerService orderCustomerService;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderPaymentEventService orderPaymentEventService;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        Order order = getExistingOrder(orderId);
        BigDecimal paidAmount = orderAmountCalculator.defaultZero(request.getPaidAmount());
        orderValidator.validateMarkPaid(order, paidAmount);

        boolean reviewed = order.getPaymentStatus() == PaymentStatus.NEED_REVIEW;
        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        order.setExpiresAt(null);
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                (reviewed ? "Đã đối soát và xác nhận thanh toán, mã thanh toán: " : "Thanh toán thành công, mã thanh toán: ")
                        + request.getPaymentCode(),
                request.getChangedBy()
        );

        // Payment-Service cũng phải ghi PAID để giao dịch không nằm mãi ở NEED_REVIEW; lỗi không chặn đơn.
        try {
            paymentClient.confirmManualPayment(savedOrder.getId(),
                    new PaymentClient.ManualPaymentConfirmRequest(request.getPaymentCode(), "Xác nhận thủ công"));
        } catch (RuntimeException exception) {
            log.warn("Không cập nhật được Payment-Service khi xác nhận tay đơn {}: {}",
                    savedOrder.getOrderCode(), exception.getMessage());
        }

        orderPaymentEventService.finalizePaidOrder(
                savedOrder, null, oldStatus, request.getChangedBy(), request.getPaymentCode());
        return orderResponseAssembler.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse cancelPosPayment(UUID orderId) {
        Order order = getExistingOrder(orderId);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new OrderAlreadyPaidException("Đơn hàng đã được thanh toán, không huỷ phiên thanh toán được");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentStatus() != PaymentStatus.WAITING_PAYMENT) {
            throw new InvalidOrderStatusException("Đơn không có phiên thanh toán đang chờ để huỷ");
        }

        // Đóng attempt bên Payment-Service trước: tiền về sau đó sẽ bị gắn cờ kiểm tra thay vì tự hoàn tất đơn.
        paymentClient.cancelPendingPayment(order.getId());

        Order savedOrder = orderPaymentEventService.returnOrderToDraft(
                order, "Thu ngân huỷ thanh toán chuyển khoản, chọn lại phương thức");
        orderPaymentSagaService.failPaymentSaga(savedOrder, null, "Thu ngân huỷ phiên thanh toán");
        return orderResponseAssembler.toResponse(savedOrder);
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }
}
