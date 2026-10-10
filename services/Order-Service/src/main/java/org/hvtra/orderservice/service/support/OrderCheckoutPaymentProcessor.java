package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.InventoryClient;
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
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
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
    private final OrderPaymentEventService orderPaymentEventService;
    private final OrderBackorderSupport orderBackorderSupport;

    public PaymentResponse checkoutPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount,
            UUID changedBy
    ) {
        return checkoutPayment(order, paymentMethod, receivedAmount, changedBy, null);
    }

    /**
     * Thanh toán khi khách đồng ý chờ hàng: hỏi Inventory xem đơn có thiếu hàng không. Thiếu thì ghi nhận là
     * đơn chờ hàng kèm ngày dự kiến có hàng (thu đủ hoặc thu cọc); đủ thì bán như đơn thường.
     *
     * @param requestedDeposit tiền cọc khách muốn trả trước, null hoặc 0 = thu đủ
     */
    public PaymentResponse checkoutBackorderPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount,
            UUID changedBy,
            BigDecimal requestedDeposit
    ) {
        InventoryClient.AvailabilityEstimateResponse estimate = orderBackorderSupport.estimate(order);
        boolean backorder = estimate != null && estimate.backorder();
        order.setBackorder(backorder);
        order.setExpectedAvailableAt(backorder ? estimate.estimatedAvailableAt() : null);
        order.setDepositAmount(BigDecimal.ZERO);

        boolean depositRequested = requestedDeposit != null && requestedDeposit.compareTo(BigDecimal.ZERO) > 0;
        if (!depositRequested) {
            return checkoutPayment(order, paymentMethod, receivedAmount, changedBy, null);
        }
        if (!backorder) {
            throw new InvalidPaymentAmountException(
                    "Chỉ đơn chờ hàng mới được đặt cọc. Đơn này đủ hàng, vui lòng thu đủ tiền");
        }
        if (requestedDeposit.compareTo(order.getFinalAmount()) >= 0) {
            return checkoutPayment(order, paymentMethod, receivedAmount, changedBy, null);
        }
        return checkoutPayment(order, paymentMethod, receivedAmount, changedBy, requestedDeposit);
    }

    /**
     * @param depositAmount đơn chờ hàng trả tiền mặt: chỉ thu tiền cọc, phần còn lại thu khi giao hàng (null = thu đủ)
     */
    public PaymentResponse checkoutPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount,
            UUID changedBy,
            BigDecimal depositAmount
    ) {
        PaymentMethod method = toSupportedPaymentMethod(paymentMethod);
        if (depositAmount == null) {
            orderValidator.validateCashPaymentAmount(order, method, receivedAmount);
        } else {
            validateDeposit(order, method, receivedAmount, depositAmount);
        }

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
        paymentRequest.setDepositAmount(depositAmount);
        paymentRequest.setDeliveryRequired(order.isFulfillmentRequired());

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

        if ("PARTIALLY_PAID".equals(paymentStatus)) {
            applyDepositResult(order, payment, oldStatus, changedBy, sagaId);
            return;
        }

        if ("PAID".equals(paymentStatus) && orderBackorderSupport.isAwaitingStock(order)) {
            applyPaidBackorderResult(order, payment, oldStatus, changedBy, sagaId);
            return;
        }

        if ("PAID".equals(paymentStatus)) {
            order.setPaymentStatus(PaymentStatus.PAID);
            OrderStatus paidOrderStatus = order.isFulfillmentRequired()
                    ? OrderStatus.PAYMENT_CONFIRMED : OrderStatus.COMPLETED;
            order.setStatus(paidOrderStatus);
            order.setPaidAmount(order.getFinalAmount());
            order.setDebtAmount(BigDecimal.ZERO);
            orderPaymentEventService.processPaidOrderInventory(
                    order, eventFromPaymentResponse(order, payment, sagaId), !order.isFulfillmentRequired());
            if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED) {
                order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
                return;
            }

            orderStatusHistoryWriter.write(
                    order,
                    oldStatus,
                    paidOrderStatus,
                    "Thanh toán POS trực tiếp bằng " + paymentMethod + ", mã thanh toán: " + order.getPaymentCode(),
                    changedBy
            );
            PaymentOrderEventPayload paidEvent = eventFromPaymentResponse(order, payment, sagaId);
            orderPaymentSagaService.completePaymentSaga(order, paidEvent, order.getPaymentCode());
            orderPaymentSagaService.markResourceConfirmationRequested(order, sagaId);
            orderCustomerService.updateCustomerPurchase(order.getId());
            orderOutboxPublisher.publishPaymentCompletedOrchestration(order, order.getPaymentCode(), sagaId.toString());
            orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                    order,
                    paidEvent,
                    oldStatus,
                    paidOrderStatus,
                    sagaId.toString()
            );
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

    /** Đơn chờ hàng đã thu đủ tiền: chưa trừ tồn, chưa hoàn tất đơn; Inventory được yêu cầu chuẩn bị hàng. */
    private void applyPaidBackorderResult(
            Order order, PaymentResponse payment, OrderStatus oldStatus, UUID changedBy, UUID sagaId) {
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        orderBackorderSupport.enterWaitingStock(order);

        orderStatusHistoryWriter.write(
                order,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                "Đã thu đủ tiền bằng " + order.getPaymentMethod() + ", mã thanh toán: " + order.getPaymentCode()
                        + ". Đơn chờ hàng, giao sau khi có hàng",
                changedBy
        );
        PaymentOrderEventPayload paidEvent = eventFromPaymentResponse(order, payment, sagaId);
        orderPaymentSagaService.completePaymentSaga(order, paidEvent, order.getPaymentCode());
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                order,
                paidEvent,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                sagaId.toString()
        );
    }

    /** Đơn chờ hàng mới thu tiền cọc: phần còn lại thu khi giao hàng. */
    private void applyDepositResult(
            Order order, PaymentResponse payment, OrderStatus oldStatus, UUID changedBy, UUID sagaId) {
        if (!orderBackorderSupport.isAwaitingStock(order)) {
            throw new InvalidPaymentAmountException("Chỉ đơn chờ hàng mới được đặt cọc");
        }
        BigDecimal deposit = payment.getAmountPaid() == null ? BigDecimal.ZERO : payment.getAmountPaid();
        order.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setPaidAmount(deposit);
        order.setDepositAmount(deposit);
        order.setDebtAmount(order.getFinalAmount().subtract(deposit).max(BigDecimal.ZERO));
        orderBackorderSupport.enterWaitingStock(order);

        orderStatusHistoryWriter.write(
                order,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                "Đã thu tiền cọc " + deposit.toPlainString() + " bằng " + order.getPaymentMethod()
                        + ", mã thanh toán: " + order.getPaymentCode() + ". Đơn chờ hàng, thu phần còn lại khi giao",
                changedBy
        );
        orderPaymentSagaService.markPaymentRequested(order, sagaId, payment);
    }

    private void validateDeposit(Order order, PaymentMethod method, BigDecimal receivedAmount, BigDecimal depositAmount) {
        if (method != PaymentMethod.CASH) {
            throw new InvalidPaymentAmountException("Đặt cọc hiện chỉ hỗ trợ thanh toán tiền mặt");
        }
        if (depositAmount.compareTo(BigDecimal.ZERO) <= 0 || depositAmount.compareTo(order.getFinalAmount()) >= 0) {
            throw new InvalidPaymentAmountException("Số tiền đặt cọc phải lớn hơn 0 và nhỏ hơn thành tiền của đơn hàng");
        }
        if (receivedAmount == null || receivedAmount.compareTo(depositAmount) < 0) {
            throw new InvalidPaymentAmountException("Số tiền khách đưa nhỏ hơn số tiền đặt cọc");
        }
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
