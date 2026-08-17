package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCheckoutPaymentProcessor {

    private final PaymentClient paymentClient;
    private final OrderValidator orderValidator;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderCustomerService orderCustomerService;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;

    public PaymentResponse checkoutPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount,
            UUID changedBy
    ) {
        PaymentMethod method = toSupportedPaymentMethod(paymentMethod);
        orderValidator.validateCashPaymentAmount(order, method, receivedAmount);

        UUID sagaId = orderPaymentSagaService.startPaymentSaga(order, method.name());

        PaymentCreateRequest paymentRequest = new PaymentCreateRequest();
        paymentRequest.setOrderId(order.getId());
        paymentRequest.setOrderCode(order.getOrderCode());
        paymentRequest.setCustomerId(order.getCustomerId());
        paymentRequest.setSagaId(sagaId);
        paymentRequest.setPaymentMethod(method);
        paymentRequest.setGoodsAmount(order.getOriginalAmount());
        paymentRequest.setDiscountAmount(order.getDiscountAmount());
        paymentRequest.setDeliveryFee(order.getShippingFee());
        paymentRequest.setFinalAmount(order.getFinalAmount());
        paymentRequest.setReceivedAmount(method == PaymentMethod.CASH ? receivedAmount : null);
        paymentRequest.setDeliveryRequired(false);

        PaymentResponse payment = paymentClient.checkout(paymentRequest);
        applyPaymentCheckoutResult(order, payment, changedBy, sagaId);
        return payment;
    }

    private void applyPaymentCheckoutResult(Order order, PaymentResponse payment, UUID changedBy, UUID sagaId) {
        orderValidator.validatePaymentCheckoutResult(payment);

        OrderStatus oldStatus = order.getStatus();
        String paymentStatus = payment.getStatus().trim().toUpperCase(java.util.Locale.ROOT);
        String paymentMethod = orderResponseAssembler.normalizePaymentMethod(payment.getMethod());

        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(payment.getPaymentCode(), order.getPaymentCode()));

        if ("PAID".equals(paymentStatus)) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(OrderStatus.COMPLETED);
            order.setPaidAmount(order.getFinalAmount());
            order.setDebtAmount(BigDecimal.ZERO);

            orderStatusHistoryWriter.write(
                    order,
                    oldStatus,
                    OrderStatus.COMPLETED,
                    "Thanh toán POS trực tiếp bằng " + paymentMethod + ", mã thanh toán: " + order.getPaymentCode(),
                    changedBy
            );
            orderPaymentSagaService.completePaymentSaga(order, eventFromPaymentResponse(order, payment, sagaId), order.getPaymentCode());
            orderCustomerService.updateCustomerPurchase(order.getId());
            orderOutboxPublisher.publishPaymentCompletedOrchestration(order, order.getPaymentCode(), sagaId.toString());
            return;
        }

        if ("WAITING_PAYMENT".equals(paymentStatus) || "PENDING".equals(paymentStatus)) {
            order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setDebtAmount(order.getFinalAmount());

            orderStatusHistoryWriter.write(
                    order,
                    oldStatus,
                    OrderStatus.PENDING_PAYMENT,
                    "Tạo yêu cầu thanh toán " + paymentMethod + ", mã thanh toán: " + order.getPaymentCode(),
                    changedBy
            );
            orderPaymentSagaService.markPaymentRequested(order, sagaId, payment);
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        String error = "Payment-Service trả về trạng thái không thành công: " + paymentStatus;
        orderStatusHistoryWriter.write(
                order,
                oldStatus,
                OrderStatus.PAYMENT_FAILED,
                error,
                changedBy
        );
        orderPaymentSagaService.failPaymentSaga(order, eventFromPaymentResponse(order, payment, sagaId), error);
    }

    private PaymentOrderEventPayload eventFromPaymentResponse(Order order, PaymentResponse payment, UUID sagaId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setSagaId(sagaId == null ? null : sagaId.toString());
        event.setOrderId(order.getId().toString());
        event.setOrderCode(order.getOrderCode());
        event.setPaymentId(payment.getPaymentId() == null ? null : payment.getPaymentId().toString());
        event.setPaymentCode(firstNonBlank(payment.getPaymentCode(), order.getPaymentCode()));
        event.setPaymentMethod(payment.getMethod());
        event.setPaymentProvider(payment.getProvider());
        event.setPaymentStatus(payment.getStatus());
        event.setAmountRequired(payment.getAmountRequired());
        event.setAmountPaid(payment.getAmountPaid());
        event.setEventType("PAID".equalsIgnoreCase(payment.getStatus()) ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED");
        event.setEventKey(order.getId().toString());
        return event;
    }

    private PaymentMethod toSupportedPaymentMethod(String paymentMethod) {
        String normalized = orderResponseAssembler.normalizePaymentMethod(paymentMethod);
        if (normalized == null) {
            throw new InvalidPaymentAmountException("Phương thức thanh toán không hợp lệ");
        }
        return switch (normalized) {
            case "CASH" -> PaymentMethod.CASH;
            case "BANK_TRANSFER" -> PaymentMethod.BANK_TRANSFER;
            default -> throw new InvalidPaymentAmountException(
                    "Payment-Service chưa hỗ trợ phương thức thanh toán: " + normalized
            );
        };
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
