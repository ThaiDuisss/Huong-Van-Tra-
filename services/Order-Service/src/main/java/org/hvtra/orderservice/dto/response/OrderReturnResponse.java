package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
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
}
