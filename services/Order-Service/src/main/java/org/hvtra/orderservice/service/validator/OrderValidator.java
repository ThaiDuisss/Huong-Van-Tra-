package org.hvtra.orderservice.service.validator;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OrderValidator {

    public void validateMarkPaid(Order order, BigDecimal paidAmount) {
        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            throw new OrderAlreadyPaidException("Đơn hàng đã được thanh toán");
        }
        validatePaymentAmount(order, paidAmount);
    }

    public void validatePaymentAmount(Order order, BigDecimal paidAmount) {
        if (defaultZero(paidAmount).compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }
    }

    public BigDecimal requirePositiveQuantity(BigDecimal value) {
        BigDecimal normalized = defaultZero(value);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderStatusException("Số lượng phải lớn hơn 0");
        }
        return normalized;
    }

    public void validateEditableDraft(Order order, Instant now) {
        if (OrderStatus.DRAFT != order.getStatus()) {
            throw new InvalidOrderStatusException("ORDER_NOT_DRAFT");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(now)) {
            throw new InvalidOrderStatusException("ORDER_EXPIRED");
        }
    }

    public void validateDraftHasItems(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidOrderStatusException("Chưa có sản phẩm trong đơn");
        }
    }

    public void validateDraftCancellation(Order order) {
        if (OrderStatus.DRAFT != order.getStatus()) {
            throw new InvalidOrderStatusException("Chỉ hóa đơn nháp mới được hủy bằng API này");
        }
    }

    public void validateCancellation(Order order) {
        if (PaymentStatus.PAID == order.getPaymentStatus() || OrderStatus.COMPLETED == order.getStatus()) {
            throw new InvalidOrderStatusException(
                    "Đơn hàng đã thanh toán hoặc đã hoàn thành, không thể hủy trực tiếp. Vui lòng dùng luồng hoàn tiền/hoàn hàng."
            );
        }
        if (PaymentStatus.PARTIALLY_PAID == order.getPaymentStatus()) {
            throw new InvalidOrderStatusException(
                    "Đơn đã thu tiền cọc, không thể hủy trực tiếp. Vui lòng hoàn cọc cho khách trước khi hủy."
            );
        }
        if (OrderStatus.CANCELLED == order.getStatus()) {
            throw new InvalidOrderStatusException("Đơn hàng đã được hủy trước đó");
        }
    }

    public void validateCashPaymentAmount(Order order, PaymentMethod method, BigDecimal receivedAmount) {
        if (method == PaymentMethod.CASH
                && defaultZero(receivedAmount).compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }
    }

    public void validatePaymentCheckoutResult(PaymentResponse payment) {
        if (payment == null || payment.getStatus() == null) {
            throw new InvalidPaymentAmountException("Payment-Service không trả về kết quả thanh toán");
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
