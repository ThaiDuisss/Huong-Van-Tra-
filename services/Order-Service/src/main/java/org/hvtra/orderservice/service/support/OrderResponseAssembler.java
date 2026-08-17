package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderResponseAssembler {

    private final PaymentClient paymentClient;
    private final OrderMapper orderMapper;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderStatusHistoryReader orderStatusHistoryReader;

    public OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    public OrderResponse toResponse(Order order, PaymentResponse payment) {
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order);
        BigDecimal changeAmount = paymentInfo == null
                ? BigDecimal.ZERO
                : orderAmountCalculator.defaultZero(paymentInfo.changeAmount());
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);
        OrderResponse response = orderMapper.toResponse(
                order,
                payment,
                changeAmount,
                paymentMethod
        );
        response.setStatusHistories(orderStatusHistoryReader.findResponses(order.getId()));
        return response;
    }

    public TodaySalesItemResponse toTodaySalesItemResponse(Order order) {
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order);
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);
        return orderMapper.toTodaySalesItemResponse(order, paymentMethod);
    }

    public boolean paymentMethodMatches(TodaySalesItemResponse item, String paymentMethod) {
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
        return normalizedPaymentMethod == null
                || normalizedPaymentMethod.equals(normalizePaymentMethod(item.getPaymentMethod()));
    }

    public String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return null;
        }
        String normalized = paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BANK", "TRANSFER", "BANK_TRANSFER", "QR_CODE" -> "BANK_TRANSFER";
            case "CASH" -> "CASH";
            case "DEBT", "CREDIT" -> "DEBT";
            case "CARD" -> "CARD";
            case "E_WALLET" -> "E_WALLET";
            case "COMBINED" -> "COMBINED";
            default -> normalized;
        };
    }

    private PaymentClient.PaymentOrderInfoResponse fetchPaymentInfo(Order order) {
        try {
            return paymentClient.getPaymentByOrderId(order.getId());
        } catch (RuntimeException ex) {
            log.debug("Không lấy được payment info cho orderId={}: {}", order.getId(), ex.getMessage());
            return null;
        }
    }

    private String resolvePaymentMethod(
            Order order,
            PaymentClient.PaymentOrderInfoResponse paymentInfo
    ) {
        if (order.getPaymentMethod() != null && !order.getPaymentMethod().isBlank()) {
            return normalizePaymentMethod(order.getPaymentMethod());
        }
        if (paymentInfo != null && paymentInfo.method() != null) {
            return normalizePaymentMethod(paymentInfo.method());
        }
        if (orderAmountCalculator.defaultZero(order.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0
                || orderAmountCalculator.isPendingPaymentSale(order)) {
            return "DEBT";
        }
        return PaymentStatus.PAID == order.getPaymentStatus() ? "CASH" : null;
    }
}
