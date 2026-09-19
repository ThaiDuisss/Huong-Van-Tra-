package org.hvtra.iventoryservice.customerreturn.service;

import org.hvtra.iventoryservice.customerreturn.dto.request.CreateCustomerReturnInspectionRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcDispositionReportRequest;
import org.hvtra.iventoryservice.customerreturn.dto.request.CustomerReturnQcSummaryBatchRequest;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnInspectionResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcDispositionReportResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.CustomerReturnQcSummaryResponse;
import org.hvtra.iventoryservice.customerreturn.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.iventoryservice.domain.enums.CustomerReturnInspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import org.hvtra.iventoryservice.customerreturn.dto.request.PostCustomerReturnInspectionRequest;
import java.util.UUID;

public interface CustomerReturnInspectionService {
    CustomerReturnInspectionResponse createInspection(UUID returnId, CreateCustomerReturnInspectionRequest request);

    CustomerReturnInspectionResponse postInspection(UUID inspectionId);

    CustomerReturnInspectionResponse postInspection(UUID inspectionId, PostCustomerReturnInspectionRequest request);

    CustomerReturnInspectionResponse getInspection(UUID inspectionId);

    Page<CustomerReturnInspectionResponse> listInspections(
            String returnCode,
            String orderCode,
            CustomerReturnInspectionStatus status,
            Pageable pageable
    );

    CustomerReturnQcSummaryResponse getQcSummary(UUID returnId);

    List<CustomerReturnQcSummaryResponse> getQcSummaries(CustomerReturnQcSummaryBatchRequest request);

    List<CustomerReturnQcDispositionReportResponse> getQcDispositionReport(
            CustomerReturnQcDispositionReportRequest request
    );

    ReturnDispositionStatusResponse getDispositionStatus(UUID commandId);
}
