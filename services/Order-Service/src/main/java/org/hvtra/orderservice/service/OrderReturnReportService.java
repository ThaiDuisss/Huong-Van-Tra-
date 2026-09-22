package org.hvtra.orderservice.service;

import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnReportDetailResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportSummaryResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderReturnReportService {

    OrderReturnReportSummaryResponse getSummary(
            UUID branchId,
            LocalDate fromDate,
            LocalDate toDate
    );

    Page<OrderReturnReportDetailResponse> getDetails(
            String keyword,
            ReturnStatus returnStatus,
            String refundStatus,
            UUID branchId,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    );
}
