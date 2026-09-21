package org.hvtra.orderservice.service.mapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMapper {

    private static final String DEFAULT_ORDER_TYPE = "POS";

    private final OrderItemMapper orderItemMapper;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderDateTimeHelper orderDateTimeHelper;

    public Order toOrder(
            CreateOrderRequest request,
            CustomerResponse customerSnapshot,
            String orderCode,
            String orderType,
            BigDecimal discountAmount,
            BigDecimal shippingFee
    ) {
        return Order.builder()
                .orderCode(orderCode)
                .tenantId(request.getTenantId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .customerNameSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getFullName(),
                        request.getCustomerNameSnapshot()
                ))
                .customerPhoneSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getPhone(),
                        request.getCustomerPhoneSnapshot()
                ))
                .customerEmailSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getEmail(),
                        request.getCustomerEmailSnapshot()
                ))
                .branchNameSnapshot(request.getBranchNameSnapshot())
                .terminalNameSnapshot(request.getTerminalNameSnapshot())
                .shiftCodeSnapshot(request.getShiftCodeSnapshot())
                .cashierId(request.getCashierId())
                .cashierNameSnapshot(request.getCashierNameSnapshot())
                .recipientNameSnapshot(request.getRecipientNameSnapshot())
                .recipientPhoneSnapshot(request.getRecipientPhoneSnapshot())
                .shippingAddressSnapshot(request.getShippingAddressSnapshot())
                .shippingProviderSnapshot(request.getShippingProviderSnapshot())
                .orderType(orderType)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(discountAmount)
                .shippingFee(shippingFee)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .build();
    }

    public Order toDraftOrder(
            AddDraftOrderItemRequest request,
            CustomerResponse customerSnapshot,
            String orderCode,
            Instant expiresAt
    ) {
        return Order.builder()
                .orderCode(orderCode)
                .tenantId(request.getTenantId())
                .branchId(request.getBranchId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .customerNameSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getFullName(),
                        request.getCustomerNameSnapshot()))
                .customerPhoneSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getPhone(),
                        request.getCustomerPhoneSnapshot()))
                .customerEmailSnapshot(customerSnapshot == null ? null : customerSnapshot.getEmail())
                .branchNameSnapshot(request.getBranchNameSnapshot())
                .terminalNameSnapshot(request.getTerminalNameSnapshot())
                .shiftCodeSnapshot(request.getShiftCodeSnapshot())
                .cashierId(request.getCashierId())
                .cashierNameSnapshot(request.getCashierNameSnapshot())
                .orderType(DEFAULT_ORDER_TYPE)
                .status(OrderStatus.DRAFT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .expiresAt(expiresAt)
                .build();
    }

    public OrderPaymentInfoResponse toPaymentInfoResponse(Order order) {
        return OrderPaymentInfoResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .finalAmount(order.getFinalAmount())
                .paidAmount(order.getPaidAmount())
                .debtAmount(order.getDebtAmount())
                .build();
    }

    public OrderResponse toResponse(
            Order order,
            PaymentResponse payment,
            BigDecimal changeAmount,
            String paymentMethod
    ) {
        LocalDateTime createdAt = orderDateTimeHelper.toLocalDateTime(auditValue(order, "getCreatedAt"));

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .tenantId(order.getTenantId())
                .branchId(order.getBranchId())
                .terminalId(order.getTerminalId())
                .shiftId(order.getShiftId())
                .customerId(order.getCustomerId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .returnStatus(order.getReturnStatus())
                .paymentMethod(paymentMethod)
                .paymentMethodLabel(toPaymentMethodLabel(paymentMethod))
                .payment(payment)
                .originalAmount(order.getOriginalAmount())
                .subTotal(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalAmount(order.getFinalAmount())
                .paidAmount(order.getPaidAmount())
                .debtAmount(order.getDebtAmount())
                .changeAmount(changeAmount)
                .expiresAt(order.getExpiresAt())
                .items(order.getItems().stream().map(orderItemMapper::toResponse).toList())
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .customerEmail(order.getCustomerEmailSnapshot())
                .branchName(order.getBranchNameSnapshot())
                .terminalName(order.getTerminalNameSnapshot())
                .shiftCode(order.getShiftCodeSnapshot())
                .cashierId(order.getCashierId())
                .cashierName(order.getCashierNameSnapshot())
                .recipientName(order.getRecipientNameSnapshot())
                .recipientPhone(order.getRecipientPhoneSnapshot())
                .shippingAddress(order.getShippingAddressSnapshot())
                .shippingProvider(order.getShippingProviderSnapshot())
                .orderTypeLabel(toOrderTypeLabel(order.getOrderType()))
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .returnStatusLabel(toReturnStatusLabel(order.getReturnStatus()))
                .createdBy(toAuditString(auditValue(order, "getCreatedBy")))
                .createdByName(order.getCashierNameSnapshot())
                .createdAt(createdAt)
                .soldAt(createdAt)
                .updatedBy(toAuditString(auditValue(order, "getUpdatedBy")))
                .updatedAt(orderDateTimeHelper.toLocalDateTime(auditValue(order, "getUpdatedAt")))
                .build();
    }

    public OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .orderType(order.getOrderType())
                .orderTypeLabel(toOrderTypeLabel(order.getOrderType()))
                .status(order.getStatus())
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatus(order.getPaymentStatus())
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .returnStatus(order.getReturnStatus())
                .returnStatusLabel(toReturnStatusLabel(order.getReturnStatus()))
                .finalAmount(order.getFinalAmount())
                .createdAt(orderDateTimeHelper.toLocalDateTime(auditValue(order, "getCreatedAt")))
                .createdBy(toAuditString(auditValue(order, "getCreatedBy")))
                .createdByName(order.getCashierNameSnapshot())
                .cashierId(order.getCashierId())
                .cashierName(order.getCashierNameSnapshot())
                .build();
    }

    public TodaySalesItemResponse toTodaySalesItemResponse(Order order, String paymentMethod) {
        LocalDateTime createdAt = orderDateTimeHelper.toLocalDateTime(auditValue(order, "getCreatedAt"));

        return TodaySalesItemResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .soldAt(createdAt)
                .createdAt(createdAt)
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .cashierName(order.getCashierNameSnapshot())
                .totalItems(orderAmountCalculator.totalItems(order))
                .finalAmount(order.getFinalAmount())
                .paymentMethod(paymentMethod)
                .paymentMethodLabel(toPaymentMethodLabel(paymentMethod))
                .status(order.getStatus())
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatus(order.getPaymentStatus())
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .build();
    }

    public OrderStatusHistoryResponse toStatusHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .oldStatus(history.getFromStatus())
                .newStatus(history.getToStatus())
                .oldStatusLabel(toOrderStatusLabel(history.getFromStatus()))
                .newStatusLabel(toOrderStatusLabel(history.getToStatus()))
                .note(history.getReason())
                .createdBy(toAuditString(history.getChangedBy()))
                .createdByName(null)
                .createdAt(orderDateTimeHelper.toLocalDateTime(history.getChangedAt()))
                .build();
    }

    private String toOrderTypeLabel(String orderType) {
        if (orderType == null) {
            return null;
        }
        return switch (orderType.toUpperCase(Locale.ROOT)) {
            case "POS" -> "Bán tại quầy";
            case "ONLINE" -> "Đơn online";
            case "RETURN" -> "Hoàn hàng";
            case "REFUND" -> "Hoàn tiền";
            case "EXCHANGE" -> "Đổi hàng";
            default -> orderType;
        };
    }

    private String toOrderStatusLabel(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_PAYMENT -> "Chờ thanh toán";
            case PAYMENT_CONFIRMED -> "Chờ duyệt";
            case COMPLETED -> "Đã hoàn thành";
            case CANCELLED -> "Đã hủy";
            case EXPIRED -> "Đã hết hạn";
            case PAYMENT_FAILED -> "Thanh toán thất bại";
        };
    }

    private String toPaymentStatusLabel(PaymentStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNPAID, WAITING_PAYMENT -> "Chưa thanh toán";
            case PAID -> "Đã thanh toán";
            case FAILED -> "Thanh toán thất bại";
            case REFUNDED -> "Đã hoàn tiền";
            case PARTIALLY_REFUNDED -> "Hoàn tiền một phần";
        };
    }

    private String toReturnStatusLabel(OrderReturnStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case NONE -> "Chưa hoàn hàng";
            case PARTIALLY_RETURNED -> "Hoàn một phần";
            case FULLY_RETURNED -> "Hoàn toàn bộ";
        };
    }

    private String toPaymentMethodLabel(String paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        return switch (paymentMethod) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER", "QR_CODE" -> "Chuyển khoản";
            case "DEBT" -> "Công nợ";
            case "CARD" -> "Thẻ";
            case "E_WALLET" -> "Ví điện tử";
            case "COMBINED" -> "Kết hợp";
            default -> paymentMethod;
        };
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String toAuditString(Object value) {
        return value == null ? null : value.toString();
    }

    private Object auditValue(Object entity, String getterName) {
        if (entity == null) {
            return null;
        }
        try {
            Method method = entity.getClass().getMethod(getterName);
            return method.invoke(entity);
        } catch (ReflectiveOperationException ex) {
            log.warn("Không đọc được audit field {} từ {}: {}", getterName, entity.getClass().getName(), ex.getMessage());
            return null;
        }
    }

}
