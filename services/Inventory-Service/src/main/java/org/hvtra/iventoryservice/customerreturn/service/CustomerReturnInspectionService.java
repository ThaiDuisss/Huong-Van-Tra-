package org.hvtra.iventoryservice.customerreturn.service;

import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;

import java.util.UUID;

public interface CustomerReturnInspectionService {
    CustomerReturnInspectionResponse createInspection(UUID returnId, CreateCustomerReturnInspectionRequest request);

    CustomerReturnInspectionResponse postInspection(UUID inspectionId);

    CustomerReturnQcSummaryResponse getQcSummary(UUID returnId);
}
