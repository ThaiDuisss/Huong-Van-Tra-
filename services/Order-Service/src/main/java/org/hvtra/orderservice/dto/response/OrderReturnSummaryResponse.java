package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

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
        this(id, returnCode, orderId, orderCode, status, createdAt, updatedAt, itemCount, customerName, customerPhone, true);
    }
}
