package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.response.OrderRefundDetailResponse;
import org.hvtra.orderservice.dto.response.OrderRefundStatsResponse;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnInventoryDispositionResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundPreviewResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnTimelineResponse;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrderReturnService {
    OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request);

    OrderReturnResponse receiveReturn(UUID returnId);

    OrderReturnResponse cancelReturn(UUID returnId);

    OrderReturnRefundResponse refundReturn(UUID returnId);

    OrderReturnInventoryDispositionResponse submitInventoryDisposition(
            UUID returnId,
            SubmitReturnInventoryDispositionRequest request
    );

    OrderReturnInventoryDispositionResponse getInventoryDisposition(UUID returnId, UUID commandId);

    Page<OrderRefundSummaryResponse> getRefunds(
            String keyword,
            OrderRefundStatus status,
            RefundProgressStatus progressStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    );

    OrderRefundDetailResponse getRefundDetail(UUID returnId);

    OrderReturnRefundPreviewResponse getRefundPreview(UUID returnId);

    OrderRefundStatsResponse getRefundStats(String keyword, LocalDate fromDate, LocalDate toDate);

    default Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return getReturns(keyword, status, null, fromDate, toDate, sort, page, size);
    }

    default Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            String displayStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return getReturns(keyword, status, displayStatus, fromDate, toDate, sort, page, size, false);
    }

    Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            String displayStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size,
            boolean warehouseActiveOnly
    );

    default Page<OrderReturnSummaryResponse> getFilteredReturns(
            String keyword,
            String returnCode,
            String orderCode,
            ReturnStatus status,
            String displayStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return getFilteredReturns(keyword, returnCode, orderCode, status, displayStatus, fromDate, toDate, sort, page, size, false);
    }

    Page<OrderReturnSummaryResponse> getFilteredReturns(
            String keyword,
            String returnCode,
            String orderCode,
            ReturnStatus status,
            String displayStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size,
            boolean warehouseActiveOnly
    );

    default Map<String, Long> getReturnStatusCounts(String keyword, LocalDate fromDate, LocalDate toDate) {
        return getReturnStatusCounts(keyword, fromDate, toDate, false);
    }

    Map<String, Long> getReturnStatusCounts(String keyword, LocalDate fromDate, LocalDate toDate, boolean warehouseActiveOnly);

    List<OrderReturnResponse> getReturnsByOrder(UUID orderId);

    OrderReturnResponse getReturnById(UUID returnId);

    List<OrderReturnTimelineResponse> getReturnTimeline(UUID returnId);

    boolean isPhysicalDispositionCompleted(UUID returnId);

    void recordPhysicalCompletion(UUID returnId, UUID actorUserId, UUID inspectionId);

    void checkAndAutoCompleteNoRefundReturn(UUID returnId);
}
