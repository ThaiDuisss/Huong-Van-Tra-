package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturnSummaryResponse {
    private UUID id;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private ReturnStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Long itemCount;
    private String customerName;
    private String customerPhone;
    private Boolean refundRequired;
    private String qcStatus;
    private BigDecimal remainingQcQuantity;
    private String inspectionStatus;
    private OrderRefundStatus refundStatus;
    private Boolean hasFailedDisposition;
    private Boolean isPendingDisposition;
    private BigDecimal postedQcQuantity;
    private String displayStatus;
    private Boolean physicalDone;
    private BigDecimal requiredQuantity;
    private BigDecimal processedQuantity;
    private BigDecimal remainingQuantity;
    private String inventoryProcessingStatus;

    public OrderReturnSummaryResponse(
            UUID id,
            String returnCode,
            UUID orderId,
            String orderCode,
            ReturnStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long itemCount,
            String customerName,
            String customerPhone
    ) {
        this(id, returnCode, orderId, orderCode, status, createdAt, updatedAt, itemCount, customerName, customerPhone, true, null, null, null, null, false, false, null, null, null, null, null, null, null);
    }

    public OrderReturnSummaryResponse(
            UUID id,
            String returnCode,
            UUID orderId,
            String orderCode,
            ReturnStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long itemCount,
            String customerName,
            String customerPhone,
            Boolean refundRequired
    ) {
        this(id, returnCode, orderId, orderCode, status, createdAt, updatedAt, itemCount, customerName, customerPhone, refundRequired, null, null, null, null, false, false, null, null, null, null, null, null, null);
    }

    public OrderReturnSummaryResponse(
            UUID id,
            String returnCode,
            UUID orderId,
            String orderCode,
            ReturnStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long itemCount,
            String customerName,
            String customerPhone,
            Boolean refundRequired,
            String qcStatus,
            BigDecimal remainingQcQuantity,
            String inspectionStatus,
            OrderRefundStatus refundStatus,
            Boolean hasFailedDisposition,
            Boolean isPendingDisposition
    ) {
        this(id, returnCode, orderId, orderCode, status, createdAt, updatedAt, itemCount, customerName, customerPhone, refundRequired, qcStatus, remainingQcQuantity, inspectionStatus, refundStatus, hasFailedDisposition, isPendingDisposition, null, null, null, null, null, null, null);
    }
}
