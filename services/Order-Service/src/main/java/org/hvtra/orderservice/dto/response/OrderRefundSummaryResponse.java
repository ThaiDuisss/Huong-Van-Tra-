package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class OrderRefundSummaryResponse {

    private UUID returnId;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private Instant returnCreatedAt;
    private ReturnStatus returnStatus;
    private String displayStatus;
    private Boolean refundRequired;
    private String qcStatus;
    private Boolean physicalDone;
    private String inventoryProcessingStatus;
    private UUID inspectionId;
    private Instant inventoryProcessedAt;
    private OrderRefundStatus refundStatus;
    private Boolean refundEligible;
    private OrderRefundBlockReason refundBlockReason;
    private BigDecimal refundAmount;
    private String paymentStatus;
    private String method;
    private LocalDateTime refundedAt;
}
