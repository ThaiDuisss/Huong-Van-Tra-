package org.hvtra.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class OrderReturnReportBaseRow {
    private UUID returnId;
    private String returnCode;
    private UUID orderId;
    private String orderCode;
    private UUID branchId;
    private String customerName;
    private String customerPhone;
    private ReturnStatus returnStatus;
    private Instant returnCreatedAt;
    private Long itemCount;
}
