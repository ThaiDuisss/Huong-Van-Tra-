package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OrderReturnInventoryDispositionResponse {
    private UUID commandId;
    private UUID returnId;
    private String returnCode;
    private OrderReturnInventoryCommandStatus status;
    private Instant createdAt;
    private Instant processedAt;
    private Instant failedAt;
    private String failureCode;
    private String failureReason;
    private UUID inspectionId;
    private List<OrderReturnInventoryDispositionLineResponse> lines;
}
