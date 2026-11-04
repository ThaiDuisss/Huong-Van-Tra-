package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnReportBaseRow;
import org.hvtra.orderservice.dto.response.OrderReturnReportCoreMetricsResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportReasonBreakdownResponse;
import org.hvtra.orderservice.dto.response.OrderReturnStatusBreakdownResponse;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReturnReportServiceImplTest {
    @Mock private OrderReturnRepository returns;
    @Mock private OrderReturnItemRepository items;
    @Mock private InventoryClient inventory;
    @Mock private PaymentClient payments;
    @InjectMocks private OrderReturnReportServiceImpl service;
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    @Test
    void emptySummaryUsesZerosAndStableBreakdownWithoutCallingExternalServices() {
        var result = service.getSummary(null, null, null);
        assertEquals(0, result.getTotalReturns());
        assertEquals(0, result.getRefundedReturnCount());
        assertEquals(BigDecimal.ZERO, result.getTotalRefundAmount());
        assertEquals(BigDecimal.ZERO, result.getReturnedQuantity());
        assertEquals(BigDecimal.ZERO, result.getAverageRefundAmount());
        assertEquals(4, result.getStatusBreakdown().size());
        assertEquals(7, result.getReasonBreakdown().size());
        assertEquals(List.of("RESTOCK", "DAMAGED", "WRITE_OFF"), result.getQcBreakdown().stream().map(row -> row.getResult()).toList());
        verifyNoInteractions(inventory, payments);
    }

    @Test
    void summaryCombinesMetricsSettledRefundsAndQcWithoutCountingUnsettledMoney() {
        OrderReturnReportBaseRow first = row(0, ReturnStatus.COMPLETED);
        OrderReturnReportBaseRow second = row(1, ReturnStatus.COMPLETED);
        OrderReturnReportBaseRow cancelled = row(2, ReturnStatus.CANCELLED);
        when(returns.findReportCoreMetrics(isNull(), isNull(), isNull(), eq(ReturnStatus.COMPLETED), eq(ReturnStatus.CANCELLED), anyList()))
                .thenReturn(new OrderReturnReportCoreMetricsResponse(3L, 2L, null, 1L, 2L));
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(List.of(first, second, cancelled));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(refund(first, "REFUNDED", "100"),
                refund(second, "REFUNDED", "101"), refund(cancelled, "NOT_FOUND", "999")));
        when(items.sumReportReturnedQuantity(isNull(), isNull(), isNull(), anyList())).thenReturn(new BigDecimal("4"));
        when(returns.findReportStatusBreakdown(null, null, null)).thenReturn(List.of(
                new OrderReturnStatusBreakdownResponse(ReturnStatus.COMPLETED, 1L),
                new OrderReturnStatusBreakdownResponse(ReturnStatus.COMPLETED, 1L)));
        when(items.findReportReasonBreakdown(null, null, null, ReturnStatus.CANCELLED)).thenReturn(List.of(
                new OrderReturnReportReasonBreakdownResponse(ReturnReason.DEFECTIVE, 2L, new BigDecimal("4"))));
        when(inventory.getCustomerReturnQcDispositionReport(any())).thenReturn(List.of(
                new InventoryClient.CustomerReturnQcDispositionReportResponse("RESTOCK", new BigDecimal("2")),
                new InventoryClient.CustomerReturnQcDispositionReportResponse("DAMAGED", BigDecimal.ONE),
                new InventoryClient.CustomerReturnQcDispositionReportResponse("WRITE_OFF", BigDecimal.ONE),
                new InventoryClient.CustomerReturnQcDispositionReportResponse("QUARANTINE", null)));
        var result = service.getSummary(null, null, null);
        assertEquals(3, result.getTotalReturns());
        assertEquals(0, result.getProcessingReturns());
        assertEquals(2, result.getRefundedReturnCount());
        assertEquals(new BigDecimal("201"), result.getTotalRefundAmount());
        assertEquals(new BigDecimal("101"), result.getAverageRefundAmount());
        assertEquals(new BigDecimal("4"), result.getReturnedQuantity());
        assertEquals(2, result.getStatusBreakdown().get(2).getCount());
        assertEquals(new BigDecimal("50.00"), result.getQcBreakdown().getFirst().getPercentage());
        assertEquals(3, result.getQcBreakdown().size());
        ArgumentCaptor<InventoryClient.CustomerReturnQcDispositionReportRequest> request =
                ArgumentCaptor.forClass(InventoryClient.CustomerReturnQcDispositionReportRequest.class);
        verify(inventory).getCustomerReturnQcDispositionReport(request.capture());
        assertEquals(List.of(first.getReturnId(), second.getReturnId()), request.getValue().returnIds());
    }

    @Test
    void detailsFiltersRefundBeforePaginationAndPreservesTotalBeyondCurrentPage() {
        var first = row(0, ReturnStatus.COMPLETED);
        var second = row(1, ReturnStatus.RECEIVED);
        var third = row(2, ReturnStatus.COMPLETED);
        when(returns.findReportBaseRows("%ret%", null, null, null, null)).thenReturn(List.of(first, second, third));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(
                refund(first, "REFUNDED", "100"), refund(second, "NOT_FOUND", null), refund(third, "REFUNDED", "200")));
        var result = service.getDetails(" RET ", null, "REFUNDED", null, null, null, "createdAt,asc", 1, 1);
        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(1, result.getNumberOfElements());
        assertEquals(third.getReturnId(), result.getContent().getFirst().getReturnId());
        assertEquals(new BigDecimal("200"), result.getContent().getFirst().getRefundAmount());
        assertEquals("PENDING", result.getContent().getFirst().getQcStatus());
        verify(payments).lookupRefundSummaries(any());
    }

    @Test
    void notRefundedFilterKeepsUnsettledReturnsAndDoesNotInventRefundAmount() {
        var settled = row(0, ReturnStatus.COMPLETED);
        var waiting = row(1, ReturnStatus.RECEIVED);
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(List.of(settled, waiting));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(refund(settled, "REFUNDED", "100")));
        var result = service.getDetails(" ", null, "NOT_REFUNDED", null, null, null, null, 0, 10);
        assertEquals(1, result.getTotalElements());
        var detail = result.getContent().getFirst();
        assertEquals(waiting.getReturnId(), detail.getReturnId());
        assertEquals("NOT_REFUNDED", detail.getRefundStatus());
        assertEquals(BigDecimal.ZERO, detail.getRefundAmount());
        assertNull(detail.getRefundedAt());
    }

    @Test
    void datesUseExplicitBusinessTimezoneAndExclusiveNextDayAndPaginationIsClamped() {
        // unit-tests Maven profile fixes timezone; this assertion prevents a machine-dependent expectation.
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), ZoneId.systemDefault());
        UUID branch = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 11, 3);
        Instant from = Instant.parse("2026-11-02T17:00:00Z");
        Instant to = Instant.parse("2026-11-03T17:00:00Z");
        when(returns.findReportBaseRows(null, ReturnStatus.RECEIVED, branch, from, to)).thenReturn(List.of());
        var result = service.getDetails(null, ReturnStatus.RECEIVED, null, branch, day, day, null, -1, 1000);
        assertEquals(0, result.getNumber());
        assertEquals(200, result.getSize());
        assertTrue(result.isEmpty());
        verify(returns).findReportBaseRows(null, ReturnStatus.RECEIVED, branch, from, to);
        verifyNoInteractions(inventory, payments);
    }

    @Test
    void emptyLaterPageDoesNotFetchUnrelatedPaymentOrInventoryData() {
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(List.of(row(0, ReturnStatus.RECEIVED)));
        var result = service.getDetails(null, null, null, null, null, null, "createdAt,desc", 10, 0);
        assertTrue(result.isEmpty());
        assertEquals(1, result.getSize());
        assertEquals(1, result.getTotalElements());
        verifyNoInteractions(inventory, payments);
    }

    @Test
    void lookupBatchesRespectHundredRecordLimitAndDeduplicateRefundIdentity() {
        var rows = IntStream.range(0, 101).mapToObj(i -> row(i, ReturnStatus.CREATED)).toList();
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(rows);
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(refund(rows.getFirst(), "REFUNDED", "100")));
        var result = service.getSummary(null, null, null);
        ArgumentCaptor<PaymentClient.RefundSummaryLookupRequest> request = ArgumentCaptor.forClass(PaymentClient.RefundSummaryLookupRequest.class);
        verify(payments, times(2)).lookupRefundSummaries(request.capture());
        assertEquals(List.of(100, 1), request.getAllValues().stream().map(batch -> batch.items().size()).toList());
        assertEquals(1, result.getRefundedReturnCount());
        assertEquals(new BigDecimal("100"), result.getTotalRefundAmount());
        verify(inventory, times(2)).getCustomerReturnQcDispositionReport(any());
    }

    @Test
    void detailQcSnapshotPreservesExactLineIdentityQuantityAndIgnoresDeletedLines() {
        var base = row(0, ReturnStatus.RECEIVED);
        Order order = Order.builder().orderCode(base.getOrderCode()).branchId(UUID.randomUUID()).build();
        order.setId(base.getOrderId());
        OrderReturn record = OrderReturn.builder().order(order).returnCode(base.getReturnCode()).status(ReturnStatus.RECEIVED).build();
        record.setId(base.getReturnId());
        OrderItem sold = OrderItem.builder().productVariantId(UUID.randomUUID()).build();
        sold.setId(UUID.randomUUID());
        OrderReturnItem first = OrderReturnItem.builder().orderItem(sold).quantity(new BigDecimal("1.25"))
                .condition(ReturnCondition.SEALED).reason(ReturnReason.DEFECTIVE).build();
        first.setId(UUID.randomUUID());
        OrderReturnItem orphan = OrderReturnItem.builder().quantity(new BigDecimal("0.25")).build();
        orphan.setId(UUID.randomUUID());
        OrderReturnItem deleted = OrderReturnItem.builder().orderItem(sold).quantity(new BigDecimal("99")).build();
        deleted.setIsDeleted(true);
        record.setItems(List.of(first, orphan, deleted));
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(List.of(base));
        when(returns.findByIdInAndIsDeletedFalseWithItems(List.of(base.getReturnId()))).thenReturn(List.of(record));
        when(inventory.getCustomerReturnQcSummaries(any())).thenReturn(List.of(
                new InventoryClient.CustomerReturnQcSummaryResponse(base.getReturnId(), base.getReturnCode(), "RECEIVED", order.getBranchId(),
                        order.getId(), order.getOrderCode(), null, "POSTED", "COMPLETED", null, NOW)));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(refund(base, "REFUNDED", "12.75")));
        var result = service.getDetails(null, null, null, null, null, null, null, 0, 20);
        assertEquals("COMPLETED", result.getContent().getFirst().getQcStatus());
        assertEquals(new BigDecimal("12.75"), result.getContent().getFirst().getRefundAmount());
        ArgumentCaptor<InventoryClient.CustomerReturnQcSummaryBatchRequest> request = ArgumentCaptor.forClass(InventoryClient.CustomerReturnQcSummaryBatchRequest.class);
        verify(inventory).getCustomerReturnQcSummaries(request.capture());
        var snapshot = request.getValue().returns().getFirst();
        assertEquals(order.getId(), snapshot.orderId());
        assertEquals(order.getBranchId(), snapshot.branchId());
        assertEquals(base.getReturnId(), snapshot.returnId());
        assertEquals(2, snapshot.lines().size());
        var line = snapshot.lines().getFirst();
        assertEquals(first.getId(), line.returnItemId());
        assertEquals(sold.getId(), line.orderItemId());
        assertEquals(sold.getProductVariantId(), line.productVariantId());
        assertEquals(new BigDecimal("1.25"), line.quantity());
        assertEquals("DEFECTIVE", line.reason());
        assertNull(snapshot.lines().getLast().orderItemId());
        assertNull(snapshot.lines().getLast().productVariantId());
        assertNull(snapshot.lines().getLast().reason());
    }

    @ParameterizedTest @ValueSource(strings = {"inventory", "payment"})
    void reportDownstreamFailurePropagatesInsteadOfInventingSuccessfulEmptyReport(String failed) {
        var base = row(0, ReturnStatus.RECEIVED);
        Order order = new Order();
        order.setId(base.getOrderId());
        OrderReturn record = OrderReturn.builder().order(order).items(List.of()).build();
        record.setId(base.getReturnId());
        when(returns.findReportBaseRows(null, null, null, null, null)).thenReturn(List.of(base));
        when(returns.findByIdInAndIsDeletedFalseWithItems(any())).thenReturn(List.of(record));
        IllegalStateException down = new IllegalStateException("downstream unavailable");
        if (failed.equals("inventory")) when(inventory.getCustomerReturnQcSummaries(any())).thenThrow(down);
        else when(payments.lookupRefundSummaries(any())).thenThrow(down);
        assertSame(down, assertThrows(IllegalStateException.class,
                () -> service.getDetails(null, null, null, null, null, null, null, 0, 20)));
        verify(returns, never()).save(any());
        verify(payments, never()).refundOrderReturn(any(), any());
    }

    private OrderReturnReportBaseRow row(int seconds, ReturnStatus status) {
        return new OrderReturnReportBaseRow(UUID.randomUUID(), "RET-" + seconds, UUID.randomUUID(), "ORD-" + seconds,
                null, "Customer", null, status, NOW.plusSeconds(seconds), 1L);
    }

    private PaymentClient.PaymentRefundSummaryResponse refund(OrderReturnReportBaseRow row, String status, String amount) {
        return new PaymentClient.PaymentRefundSummaryResponse(row.getReturnId(), row.getOrderId(), UUID.randomUUID(),
                "REFUNDED".equals(status) ? UUID.randomUUID() : null, amount == null ? null : new BigDecimal(amount), null,
                new BigDecimal("1000"), status, "PARTIALLY_REFUNDED", "CASH", LocalDateTime.of(2026, 11, 3, 7, 0));
    }
}
