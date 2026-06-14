package org.hvtra.orderservice.domain.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
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
    private String status;
    private String paymentStatus;
    private String fulfillmentStatus;

    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private BigDecimal debtAmount;

    private List<OrderItemResponse> items;
}