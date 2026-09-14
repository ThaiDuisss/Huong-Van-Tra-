package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderRefundDetailResponse {

    private UUID returnId;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private Instant returnCreatedAt;
    private ReturnStatus returnStatus;
    private Boolean refundRequired;
    private String qcStatus;
    private OrderRefundStatus refundStatus;
    private BigDecimal refundAmount;
    private BigDecimal totalAmountRefunded;
    private String paymentStatus;
    private String method;
    private LocalDateTime refundedAt;
    private List<OrderReturnItemResponse> items;
}
