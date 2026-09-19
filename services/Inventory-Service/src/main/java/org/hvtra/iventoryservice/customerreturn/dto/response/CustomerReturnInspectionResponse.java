package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerReturnInspectionResponse(
        UUID id,
        String inspectionCode,
        UUID returnId,
        String returnCode,
        UUID branchId,
        UUID orderId,
        String orderCode,
        String customerName,
        CustomerReturnInspectionStatus status,
        Instant createdAt,
        Instant postedAt,
        String note,
        List<CustomerReturnInspectionLineResponse> lines
) {
    public CustomerReturnInspectionResponse(
            UUID id,
            String inspectionCode,
            UUID returnId,
            String returnCode,
            UUID branchId,
            UUID orderId,
            String orderCode,
            CustomerReturnInspectionStatus status,
            Instant createdAt,
            Instant postedAt,
            String note,
            List<CustomerReturnInspectionLineResponse> lines
    ) {
        this(id, inspectionCode, returnId, returnCode, branchId, orderId, orderCode, null, status, createdAt, postedAt, note, lines);
    }
}
