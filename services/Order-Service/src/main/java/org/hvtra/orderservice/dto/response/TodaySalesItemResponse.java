package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TodaySalesItemResponse {
    private UUID orderId;
    private String orderCode;
    private LocalDateTime soldAt;
    private LocalDateTime createdAt;
    private String customerName;
    private String customerPhone;
    private String cashierName;
    private BigDecimal totalItems;
    private BigDecimal finalAmount;
    private String paymentMethod;
    private String paymentMethodLabel;
    private OrderStatus status;
    private String statusLabel;
    private PaymentStatus paymentStatus;
    private String paymentStatusLabel;
}
