package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcStatus;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnStatus;

import java.util.List;
import java.util.UUID;

public record CustomerReturnQcSummaryResponse(
        UUID returnId,
        String returnCode,
        OrderReturnStatus returnStatus,
        UUID branchId,
        UUID orderId,
        String orderCode,
        UUID inspectionId,
        CustomerReturnInspectionStatus inspectionStatus,
        CustomerReturnQcStatus qcStatus,
        List<CustomerReturnQcSummaryLineResponse> lines
) {
}
