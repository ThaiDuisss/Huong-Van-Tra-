package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderReturnReportDetailResponse {
    private UUID returnId;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private UUID branchId;
    private String customerName;
    private String customerPhone;
    private ReturnStatus returnStatus;
    private String qcStatus;
    private BigDecimal refundAmount;
    private String method;
    private String paymentStatus;
    private String refundStatus;
    private Instant returnCreatedAt;
    private LocalDateTime refundedAt;
}
