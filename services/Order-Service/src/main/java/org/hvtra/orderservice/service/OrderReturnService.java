package org.hvtra.orderservice.service;

import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderRefundDetailResponse;
import org.hvtra.orderservice.dto.response.OrderRefundStatsResponse;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundPreviewResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderReturnService {
    OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request);

    OrderReturnResponse receiveReturn(UUID returnId);

    OrderReturnResponse cancelReturn(UUID returnId);

    OrderReturnRefundResponse refundReturn(UUID returnId);

    Page<OrderRefundSummaryResponse> getRefunds(
            String keyword,
            OrderRefundStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    );

    OrderRefundDetailResponse getRefundDetail(UUID returnId);

    OrderReturnRefundPreviewResponse getRefundPreview(UUID returnId);

    OrderRefundStatsResponse getRefundStats(String keyword, LocalDate fromDate, LocalDate toDate);

    Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    );

    List<OrderReturnResponse> getReturnsByOrder(UUID orderId);

    OrderReturnResponse getReturnById(UUID returnId);
}
