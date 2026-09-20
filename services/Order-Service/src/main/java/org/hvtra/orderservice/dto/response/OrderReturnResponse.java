package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturnResponse {
    private UUID id;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private String customerName;
    private String customerPhone;
    private UUID branchId;
    private ReturnStatus status;
    private String note;
    private Boolean refundRequired;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderReturnItemResponse> items;
    private Boolean physicalDone;
    private BigDecimal requiredQuantity;
    private BigDecimal processedQuantity;
    private BigDecimal remainingQuantity;
    private String inventoryProcessingStatus;
    private String displayStatus;
}
