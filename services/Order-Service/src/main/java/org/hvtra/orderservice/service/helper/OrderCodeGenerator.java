package org.hvtra.orderservice.service.helper;

import org.hvtra.orderservice.domain.entity.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class OrderCodeGenerator {

    private static final DateTimeFormatter ORDER_CODE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public String generateOrderCode() {
        return "ORD" + LocalDateTime.now().format(ORDER_CODE_FORMAT);
    }

    public String generateReturnCode() {
        return "RET" + LocalDateTime.now().format(ORDER_CODE_FORMAT);
    }

    public String generatePosPaymentCode(Order order, String paymentMethod) {
        return "POS-%s-%s".formatted(
                paymentMethod,
                order.getOrderCode() != null ? order.getOrderCode() : order.getId());
    }
}
