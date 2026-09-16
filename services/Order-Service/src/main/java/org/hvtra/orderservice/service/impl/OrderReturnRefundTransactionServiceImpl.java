package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.OrderReturnRefundTransactionService;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnRefundTransactionServiceImpl implements OrderReturnRefundTransactionService {

    private final OrderReturnRepository orderReturnRepository;
    private final OrderRepository orderRepository;
    private final OrderReturnHistoryWriter orderReturnHistoryWriter;
    private final OrderOutboxPublisher orderOutboxPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderReturnRefundResponse applyRefundSuccess(
            UUID returnId,
            PaymentClient.PaymentRefundResponse paymentRefund,
            UUID actorUserId
    ) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));

        PaymentStatus paymentStatus = parsePaymentStatus(paymentRefund.paymentStatus());
        if (paymentStatus != PaymentStatus.PARTIALLY_REFUNDED && paymentStatus != PaymentStatus.REFUNDED) {
            throw new InvalidOrderReturnException("Payment refund is not completed");
        }

        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(
                orderReturn.getOrder().getId()
        ).orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));

        List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(
                lockedOrder.getId()
        );

        // Keep returnStatus as RECEIVED - lifecycle is strictly CREATED -> RECEIVED and CREATED -> CANCELLED
        orderReturn.setStatus(ReturnStatus.RECEIVED);
        lockedOrder.setPaymentStatus(paymentStatus);
        lockedOrder.setReturnStatus(resolveOrderReturnStatus(lockedOrder, orderReturns));

        orderReturnRepository.save(orderReturn);
        orderRepository.save(lockedOrder);

        String refundTransactionId = paymentRefund.refundTransactionId() == null
                ? null
                : paymentRefund.refundTransactionId().toString();

        orderReturnHistoryWriter.writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.REFUND_COMPLETED,
                ReturnStatus.RECEIVED,
                ReturnStatus.RECEIVED,
                actorUserId,
                "Refund completed",
                "PAYMENT_TRANSACTION",
                refundTransactionId
        );

        orderOutboxPublisher.publishReturnRefundCompletedNotification(
                orderReturn,
                ReturnStatus.RECEIVED,
                actorUserId,
                paymentRefund
        );

        return OrderReturnRefundResponse.builder()
                .returnId(orderReturn.getId())
                .orderId(lockedOrder.getId())
                .paymentId(paymentRefund.paymentId())
                .refundTransactionId(paymentRefund.refundTransactionId())
                .refundAmount(paymentRefund.refundAmount())
                .totalAmountRefunded(paymentRefund.totalAmountRefunded())
                .paymentStatus(paymentRefund.paymentStatus())
                .returnStatus(orderReturn.getStatus())
                .orderPaymentStatus(lockedOrder.getPaymentStatus())
                .orderReturnStatus(lockedOrder.getReturnStatus())
                .method(paymentRefund.method())
                .refundedAt(paymentRefund.refundedAt())
                .build();
    }

    private OrderReturnStatus resolveOrderReturnStatus(Order order, List<OrderReturn> orderReturns) {
        Map<UUID, BigDecimal> receivedReturnedQuantities = orderReturns.stream()
                .filter(returnRecord -> ReturnStatus.RECEIVED == returnRecord.getStatus()
                        || ReturnStatus.COMPLETED == returnRecord.getStatus())
                .flatMap(returnRecord -> (returnRecord.getItems() == null
                        ? List.<OrderReturnItem>of()
                        : returnRecord.getItems()).stream())
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .filter(item -> item.getOrderItem() != null && item.getOrderItem().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getOrderItem().getId(),
                        item -> defaultZero(item.getQuantity()),
                        BigDecimal::add
                ));

        boolean anyReturned = receivedReturnedQuantities.values().stream()
                .anyMatch(quantity -> quantity.compareTo(BigDecimal.ZERO) > 0);
        if (!anyReturned) {
            return OrderReturnStatus.NONE;
        }

        boolean fullyReturned = order.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .allMatch(item -> defaultZero(receivedReturnedQuantities.get(item.getId()))
                        .compareTo(defaultZero(item.getQuantity())) >= 0);
        return fullyReturned ? OrderReturnStatus.FULLY_RETURNED : OrderReturnStatus.PARTIALLY_RETURNED;
    }

    private PaymentStatus parsePaymentStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderReturnException("Payment refund is not completed");
        }
        try {
            return PaymentStatus.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderReturnException("Invalid payment status: " + value);
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
