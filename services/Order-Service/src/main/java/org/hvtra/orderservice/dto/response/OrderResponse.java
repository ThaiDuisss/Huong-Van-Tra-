package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderResponse {
    private UUID id;
    private String orderCode;

    private UUID tenantId;
    private UUID branchId;
    private UUID terminalId;
    private UUID shiftId;
    private UUID customerId;

    private String orderType;
    private OrderStatus status;
    private PaymentStatus paymentStatus;

    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal debtAmount;

    private List<OrderItemResponse> items;

    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private String branchName;
    private String terminalName;
    private String shiftCode;

    private UUID cashierId;
    private String cashierName;

    private String recipientName;
    private String recipientPhone;
    private String shippingAddress;
    private String shippingProvider;

    private String orderTypeLabel;
    private String statusLabel;
    private String paymentStatusLabel;

    private String note;

    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    private String updatedBy;
    private LocalDateTime updatedAt;

    private List<OrderStatusHistoryResponse> statusHistories;
}
