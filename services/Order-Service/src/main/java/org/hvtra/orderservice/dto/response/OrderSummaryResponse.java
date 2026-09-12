package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderSummaryResponse {
    private UUID id;
    private String orderCode;

    private String customerName;
    private String customerPhone;

    private String orderType;
    private String orderTypeLabel;

    private OrderStatus status;
    private String statusLabel;

    private PaymentStatus paymentStatus;
    private String paymentStatusLabel;

    private OrderReturnStatus returnStatus;
    private String returnStatusLabel;

    private BigDecimal finalAmount;

    private LocalDateTime createdAt;

    private String createdBy;
    private String createdByName;

    private UUID cashierId;
    private String cashierName;
}
