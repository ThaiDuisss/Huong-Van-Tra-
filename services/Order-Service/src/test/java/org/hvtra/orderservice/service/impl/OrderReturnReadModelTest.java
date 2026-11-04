package org.hvtra.orderservice.service.impl;

import org.hvtra.commonservice.common_core.exception.FeignClientException;
import feign.FeignException;
import feign.RetryableException;
import feign.Request;
import feign.Response;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionLineRequest;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderItemRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.OrderReturnRefundTransactionService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnCreationEligibilityResolver;
import org.hvtra.orderservice.service.support.OrderReturnDisplayStatusResolver;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgress;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgressResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderReturnReadModelTest {
    @Mock private OrderRepository orders;
    @Mock private OrderItemRepository orderItems;
    @Mock private OrderReturnRepository returns;
    @Mock private OrderReturnItemRepository returnItems;
    @Mock private OrderReturnInventoryCommandRepository commands;
    @Mock private OrderReturnHistoryRepository histories;
    @Mock private OrderCodeGenerator codes;
    @Mock private InventoryClient inventory;
    @Mock private PaymentClient payments;
    @Mock private ReturnRefundCalculator calculator;
    @Mock private OrderReturnHistoryWriter historyWriter;
    @Mock private OrderOutboxPublisher outbox;
    @Mock private OrderReturnRefundTransactionService refundWriter;
    @Mock private OrderReturnInventoryCommandReconciliationWriter commandWriter;
    @Mock private OrderReturnPhysicalProgressResolver progress;
    @Mock private OrderReturnPhysicalCompletionHandler completion;
    private OrderReturnServiceImpl service;
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OrderReturnServiceImpl(orders, orderItems, returns, returnItems, commands, histories, codes,
                new OrderReturnMapper(), inventory, payments, calculator, historyWriter, outbox, refundWriter,
                commandWriter, progress, new OrderReturnDisplayStatusResolver(), new OrderReturnRefundEligibilityResolver(),
                completion, new OrderReturnCreationEligibilityResolver());
    }

    @Test
    void emptyRefundListAndCountersNeverRequestPaymentOrInventory() {
        var page = service.getRefunds(null, null, null, null, null, null, -1, 0);
        var stats = service.getRefundStats(null, null, null);
        assertTrue(page.isEmpty());
        assertEquals(0, page.getNumber());
        assertEquals(1, page.getSize());
        assertEquals(0, stats.getTotal());
        assertEquals(0, stats.getPendingRefund());
        verifyNoInteractions(inventory, payments, outbox, refundWriter);
    }

    @Test
    void warehousePriorityAndCountsAreDerivedAcrossAllCandidatesBeforePageSlice() {
        Fixture fixture = fixture(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.RECEIVED,
                ReturnStatus.RECEIVED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED, ReturnStatus.CANCELLED);
        fixture.progress().put(fixture.rows().get(1).getId(), physical(false, "FAILED", false, true));
        fixture.progress().put(fixture.rows().get(2).getId(), physical(false, "PROCESSING", true, false));
        fixture.progress().put(fixture.rows().get(4).getId(), physical(true, "COMPLETED", false, false));
        stubBatchProgress(fixture);
        LocalDate day = LocalDate.of(2026, 11, 3);
        Instant from = Instant.parse("2026-11-02T17:00:00Z");
        Instant to = Instant.parse("2026-11-03T17:00:00Z");
        when(returns.findFilteredReturnSummariesList("%tea%", "%ret%", "%ord%", from, to)).thenReturn(fixture.rows());
        var page = service.getFilteredReturns(" TEA ", " RET ", " ORD ", null, "ALL", day, day, null, 0, 2, true);
        assertEquals(6, page.getTotalElements());
        assertEquals(List.of(fixture.rows().get(1).getId(), fixture.rows().get(2).getId()),
                page.getContent().stream().map(OrderReturnSummaryResponse::getId).toList());
        when(returns.findReturnSummariesList("%tea%", null, from, to)).thenReturn(fixture.rows());
        var warehouse = service.getReturnStatusCounts(" TEA ", day, day, true);
        var all = service.getReturnStatusCounts(" TEA ", day, day, false);
        assertEquals(6L, warehouse.get("ALL"));
        assertFalse(warehouse.containsKey("PENDING_REFUND"));
        assertEquals(7L, all.get("ALL"));
        for (String status : List.of("CREATED", "RECEIVED", "PROCESSING", "PROCESSING_FAILED", "COMPLETED", "CANCELLED", "PENDING_REFUND")) {
            assertEquals(1L, all.get(status), status);
        }
        verifyNoInteractions(payments, outbox, refundWriter);
    }

    @Test
    void displayStatusFilterWorksOutsideWarehouseAndKeepsTotalWhenPageIsEmpty() {
        Fixture fixture = fixture(ReturnStatus.RECEIVED, ReturnStatus.RECEIVED, ReturnStatus.CREATED);
        fixture.progress().put(fixture.rows().getFirst().getId(), physical(false, "FAILED", false, true));
        stubBatchProgress(fixture);
        when(returns.findFilteredReturnSummariesList(null, null, null, null, null)).thenReturn(fixture.rows());
        var page = service.getFilteredReturns(null, null, null, null, "PROCESSING_FAILED", null, null, null, 0, 10, false);
        assertEquals(1, page.getTotalElements());
        assertEquals("PROCESSING_FAILED", page.getContent().getFirst().getDisplayStatus());
        var later = service.getFilteredReturns(null, null, null, null, "PROCESSING_FAILED", null, null, null, 1, 10, false);
        assertTrue(later.isEmpty());
        assertEquals(1, later.getTotalElements());
    }

    @Test
    void refundCountersAndFilteringUseAllStatesBeforePagination() {
        Fixture fixture = fixture(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.RECEIVED,
                ReturnStatus.COMPLETED, ReturnStatus.CANCELLED, ReturnStatus.RECEIVED);
        fixture.progress().put(fixture.rows().get(2).getId(), physical(true, "COMPLETED", false, false));
        fixture.progress().put(fixture.rows().get(5).getId(), physical(false, "FAILED", false, true));
        fixture.records().get(4).setRefundRequired(false);
        fixture.rows().get(4).setRefundRequired(false);
        stubBatchProgress(fixture);
        when(returns.findRefundReturnSummaries(null, null, null)).thenReturn(fixture.rows());
        when(orders.findAllWithItemsByIdIn(anyList())).thenReturn(List.of(fixture.order()));
        when(returns.findByOrderIdInAndIsDeletedFalseWithItems(anyList())).thenReturn(fixture.records());
        when(calculator.calculate(eq(fixture.order()), any(), eq(fixture.records())))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("100")));
        when(payments.lookupRefundSummaries(any())).thenReturn(fixture.rows().stream().map(row -> {
            if (row == fixture.rows().get(5)) {
                return new PaymentClient.PaymentRefundSummaryResponse(row.getId(), row.getOrderId(), UUID.randomUUID(), null,
                        null, BigDecimal.ZERO, new BigDecimal("300"), "FAILED", "PAID", "CASH", null);
            }
            return payment(row, row.getStatus() == ReturnStatus.COMPLETED);
        }).toList());
        var page = service.getRefunds(null, OrderRefundStatus.PENDING, RefundProgressStatus.WAITING_REFUND, null, null, "createdAt,asc", 0, 10);
        assertEquals(1, page.getTotalElements());
        assertEquals(fixture.rows().get(2).getId(), page.getContent().getFirst().getReturnId());
        assertTrue(page.getContent().getFirst().getRefundEligible());
        assertEquals(new BigDecimal("100"), page.getContent().getFirst().getRefundAmount());
        var stats = service.getRefundStats(null, null, null);
        assertEquals(6, stats.getTotal());
        assertEquals(1, stats.getPendingRefund());
        assertEquals(1, stats.getRefunded());
        assertEquals(1, stats.getWaitingReceipt());
        assertEquals(1, stats.getWaitingInspection());
        assertEquals(1, stats.getOther());
        assertEquals(1, stats.getFailed());
        var unfiltered = service.getRefunds(null, null, null, null, null, "createdAt,desc", 0, 10);
        assertEquals(6, unfiltered.getTotalElements());
        assertEquals(RefundProgressStatus.FAILED, unfiltered.getContent().getFirst().getProgressStatus());
        assertEquals(RefundProgressStatus.WAITING_REFUND, unfiltered.getContent().get(1).getProgressStatus());
        assertEquals(6, unfiltered.getContent().stream().map(row -> row.getProgressStatus()).distinct().count());
        verifyNoInteractions(outbox, refundWriter);
    }

    @ParameterizedTest
    @CsvSource({"updatedAt,updatedAt", "returnCode,returnCode", "createdAt,createdAt", "arbitrarySql,createdAt"})
    void persistedStatusFiltersNormalizeKeywordDatesAndAllowOnlyKnownSortColumns(String requested, String effective) {
        LocalDate day = LocalDate.of(2026, 11, 3);
        Instant from = Instant.parse("2026-11-02T17:00:00Z");
        Instant to = Instant.parse("2026-11-03T17:00:00Z");
        OrderReturnSummaryResponse row = OrderReturnSummaryResponse.builder().id(UUID.randomUUID()).status(ReturnStatus.CREATED).refundRequired(true).build();
        when(returns.findFilteredReturnSummaries(eq("%tea%"), eq("%ret%"), eq("%ord%"), eq(ReturnStatus.CREATED), eq(from), eq(to), any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        service.getFilteredReturns(" TEA ", " RET ", " ORD ", ReturnStatus.CREATED, null, day, day, requested + ",asc", -1, 1000, false);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(returns).findFilteredReturnSummaries(eq("%tea%"), eq("%ret%"), eq("%ord%"), eq(ReturnStatus.CREATED), eq(from), eq(to), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(200, pageable.getValue().getPageSize());
        assertTrue(pageable.getValue().getSort().getOrderFor(effective).isAscending());
        assertTrue(pageable.getValue().getSort().getOrderFor("id").isDescending());
        assertEquals("CREATED", row.getDisplayStatus());
        assertEquals(OrderRefundStatus.NOT_READY, row.getRefundStatus());
        verifyNoInteractions(inventory, payments, refundWriter);
    }

    @ParameterizedTest
    @CsvSource({"detail,Inventory,raw", "detail,Payment,raw", "list,Inventory,raw", "list,Payment,raw",
            "detail,Inventory,raw502", "detail,Payment,raw502", "list,Inventory,raw502", "list,Payment,raw502",
            "detail,Inventory,raw504", "detail,Payment,raw504", "list,Inventory,raw504", "list,Payment,raw504",
            "detail,Inventory,timeout", "detail,Payment,timeout", "list,Inventory,timeout", "list,Payment,timeout"})
    void readModelsDistinguishTransportFailureFromMissingOrZeroFinancialData(String operation, String failedService, String failureType) {
        Fixture fixture = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = fixture.records().getFirst();
        var done = physical(true, "COMPLETED", false, false);
        when(calculator.calculate(fixture.order(), record, fixture.records()))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("100")));
        if ("detail".equals(operation)) {
            when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
            when(orders.findByIdAndIsDeletedFalse(fixture.order().getId())).thenReturn(Optional.of(fixture.order()));
            when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(fixture.order().getId())).thenReturn(fixture.records());
            when(progress.resolve(record)).thenReturn(done);
        } else {
            fixture.progress().put(record.getId(), done);
            stubBatchProgress(fixture);
            when(returns.findRefundReturnSummaries(null, null, null)).thenReturn(fixture.rows());
            when(orders.findAllWithItemsByIdIn(anyList())).thenReturn(List.of(fixture.order()));
            when(returns.findByOrderIdInAndIsDeletedFalseWithItems(anyList())).thenReturn(fixture.records());
        }
        RuntimeException failure = transportFailure(failureType);
        if ("Inventory".equals(failedService)) {
            when(payments.lookupRefundSummaries(any())).thenReturn(List.of(payment(fixture.rows().getFirst(), false)));
            if ("detail".equals(operation)) {
                when(inventory.getCustomerReturnQcSummary(record.getId())).thenThrow(failure);
            } else {
                when(inventory.getCustomerReturnQcSummaries(any())).thenThrow(failure);
            }
        } else {
            when(payments.lookupRefundSummaries(any())).thenThrow(failure);
        }
        String qcStatus;
        String paymentStatus;
        boolean eligible;
        if ("detail".equals(operation)) {
            var detail = service.getRefundDetail(record.getId());
            qcStatus = detail.getQcStatus();
            paymentStatus = detail.getPaymentStatus();
            eligible = detail.getRefundEligible();
            if ("Payment".equals(failedService)) {
                assertNull(detail.getTotalAmountRefunded());
                assertEquals(OrderRefundBlockReason.PAYMENT_SERVICE_UNAVAILABLE, detail.getRefundBlockReason());
            }
        } else {
            var row = service.getRefunds(null, null, null, null, null, null, 0, 10).getContent().getFirst();
            qcStatus = row.getQcStatus();
            paymentStatus = row.getPaymentStatus();
            eligible = row.getRefundEligible();
            if ("Payment".equals(failedService)) {
                assertEquals(OrderRefundBlockReason.PAYMENT_SERVICE_UNAVAILABLE, row.getRefundBlockReason());
            }
        }
        assertEquals("Inventory".equals(failedService), "UNAVAILABLE".equals(qcStatus));
        assertEquals("Payment".equals(failedService) ? "UNAVAILABLE" : "PAID", paymentStatus);
        // Physical completion is authoritative from processed Order commands, independent of optional QC metadata lookup.
        assertEquals(!"Payment".equals(failedService), eligible);
        verify(orders, never()).save(any());
        verify(returns, never()).save(any());
        verifyNoInteractions(refundWriter, outbox);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 502, 503, 504})
    void unavailableDownstreamDetailIsExplicitAndCannotBecomeEligibleRefund(int status) {
        Fixture fixture = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = fixture.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        when(orders.findByIdAndIsDeletedFalse(fixture.order().getId())).thenReturn(Optional.of(fixture.order()));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(fixture.order().getId())).thenReturn(fixture.records());
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        when(calculator.calculate(fixture.order(), record, fixture.records()))
                .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("100")));
        when(inventory.getCustomerReturnQcSummary(record.getId())).thenThrow(new FeignClientException(status, "qc", "unavailable", null));
        when(payments.lookupRefundSummaries(any())).thenThrow(new FeignClientException(status, "refundLookup", "unavailable", null));
        var detail = service.getRefundDetail(record.getId());
        assertEquals("UNAVAILABLE", detail.getQcStatus());
        assertEquals("UNAVAILABLE", detail.getPaymentStatus());
        assertEquals("UNKNOWN", detail.getMethod());
        assertNull(detail.getTotalAmountRefunded());
        assertFalse(detail.getRefundEligible());
        assertEquals(OrderRefundBlockReason.PAYMENT_SERVICE_UNAVAILABLE, detail.getRefundBlockReason());
        verify(orders, never()).save(any());
        verify(returns, never()).save(any());
        verifyNoInteractions(outbox, refundWriter);
    }

    @Test
    void downstreamBusinessErrorIsPropagatedInsteadOfDisguisedAsServiceUnavailable() {
        Fixture fixture = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = fixture.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        var failure = new FeignClientException(400, "qc", "invalid business input", null);
        when(inventory.getCustomerReturnQcSummary(record.getId())).thenThrow(failure);
        assertSame(failure, assertThrows(FeignClientException.class, () -> service.getRefundDetail(record.getId())));
        verifyNoInteractions(payments, outbox, refundWriter);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void previewUsesCurrentOrderMoneyAndBlocksWhenCalculationCannotBeMade(boolean calculationFails) {
        Fixture fixture = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = fixture.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        when(orders.findAllWithItemsByIdIn(List.of(fixture.order().getId()))).thenReturn(List.of(fixture.order()));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(fixture.order().getId())).thenReturn(fixture.records());
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(payment(fixture.rows().getFirst(), false)));
        if (calculationFails) {
            when(calculator.calculate(fixture.order(), record, fixture.records())).thenThrow(new IllegalStateException("missing monetary snapshot"));
        } else {
            when(calculator.calculate(fixture.order(), record, fixture.records()))
                    .thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("100")));
        }
        var preview = service.getRefundPreview(record.getId());
        assertEquals(!calculationFails, preview.isEligible());
        assertEquals(record.getId(), preview.getReturnId());
        assertEquals(fixture.order().getId(), preview.getOrderId());
        if (calculationFails) {
            assertNull(preview.getRefundAmount());
            assertEquals(OrderRefundBlockReason.NO_REFUND_AMOUNT, preview.getBlockReason());
        } else {
            assertEquals(new BigDecimal("100"), preview.getRefundAmount());
            assertNull(preview.getBlockReason());
        }
        verify(orders, never()).save(any());
        verify(returns, never()).save(any());
        verifyNoInteractions(outbox, refundWriter);
    }

    @Test
    void settledRefundDetailUsesFrozenPaymentLedgerAmountWithoutRecalculating() {
        Fixture fixture = fixture(ReturnStatus.COMPLETED);
        OrderReturn record = fixture.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        var settled = payment(fixture.rows().getFirst(), true);
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(settled));
        var detail = service.getRefundDetail(record.getId());
        assertEquals(OrderRefundStatus.REFUNDED, detail.getRefundStatus());
        assertFalse(detail.getRefundEligible());
        assertEquals(OrderRefundBlockReason.ALREADY_REFUNDED, detail.getRefundBlockReason());
        assertEquals(settled.refundAmount(), detail.getRefundAmount());
        assertEquals(settled.totalAmountRefunded(), detail.getTotalAmountRefunded());
        verifyNoInteractions(calculator, outbox, refundWriter);
    }

    @ParameterizedTest
    @CsvSource({"false,ALL,3", "true,ALL,2", "false,RECEIVED,1", "true,RECEIVED,1", "false,PENDING_REFUND,1", "true,PENDING_REFUND,0"})
    void returnsListFiltersDerivedPhysicalStateBeforePagination(boolean warehouse, String status, long count) {
        Fixture f = fixture(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.RECEIVED);
        f.progress().put(f.rows().get(2).getId(), physical(true, "COMPLETED", false, false));
        stubBatchProgress(f);
        when(returns.findReturnSummariesList(null, null, null, null)).thenReturn(f.rows());
        var page = service.getReturns(null, null, status, null, null, "createdAt,desc", 0, 1, warehouse);
        assertEquals(count, page.getTotalElements());
        assertEquals(Math.min(count, 1), page.getNumberOfElements());
        var beyond = service.getReturns(null, null, status, null, null, null, 9, 1, warehouse);
        assertTrue(beyond.isEmpty());
        assertEquals(count, beyond.getTotalElements());
        verifyNoInteractions(payments, outbox, refundWriter);
    }

    @Test
    void completedHistoryAndQcTotalsEnrichReturnWithoutReopeningCompletedOrFailedCommands() {
        Fixture f = fixture(ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
        f.progress().put(f.rows().getFirst().getId(), physical(true, "COMPLETED", true, true));
        stubBatchProgress(f);
        when(histories.findCompletedRefundReturnIds(anyList())).thenReturn(Set.of(f.rows().getFirst().getId()));
        when(returns.findReturnSummariesList(null, null, null, null)).thenReturn(f.rows());
        InventoryClient.CustomerReturnQcLineResponse line = new InventoryClient.CustomerReturnQcLineResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("3"),
                new BigDecimal("1"), new BigDecimal("2"), "POSTED");
        when(inventory.getCustomerReturnQcSummaries(any())).thenReturn(List.of(
                new InventoryClient.CustomerReturnQcSummaryResponse(f.rows().getFirst().getId(), "RET-UNIT", "RECEIVED", null,
                        f.order().getId(), "ORDER-UNIT", null, "POSTED", "QC_DONE", List.of(line), NOW)));
        var result = service.getReturns(null, null, null, null, null, null, 0, 10, false);
        var row = result.getContent().stream().filter(r -> r.getId().equals(f.rows().getFirst().getId())).findFirst().orElseThrow();
        assertEquals("COMPLETED", row.getDisplayStatus());
        assertEquals(OrderRefundStatus.REFUNDED, row.getRefundStatus());
        assertEquals("QC_DONE", row.getQcStatus());
        assertEquals(new BigDecimal("1"), row.getPostedQcQuantity());
        assertEquals(new BigDecimal("2"), row.getRemainingQcQuantity());
        assertFalse(row.getHasFailedDisposition());
        assertFalse(row.getIsPendingDisposition());
        assertTrue(result.getContent().stream().allMatch(r -> Boolean.TRUE.equals(r.getPhysicalDone())));
        verifyNoInteractions(payments, outbox, refundWriter);
    }

    @Test
    void multipleReturnsOfSameOrderKeepIndependentPhysicalAndRefundDisplayStates() {
        Fixture f = fixture(ReturnStatus.RECEIVED, ReturnStatus.RECEIVED, ReturnStatus.CANCELLED);
        f.progress().put(f.records().get(1).getId(), physical(true, "COMPLETED", false, false));
        when(orders.findByIdAndIsDeletedFalse(f.order().getId())).thenReturn(Optional.of(f.order()));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(f.order().getId())).thenReturn(f.records());
        when(progress.resolveBatch(f.records())).thenReturn(f.progress());
        var result = service.getReturnsByOrder(f.order().getId());
        assertEquals(3, result.size());
        assertEquals(List.of("RECEIVED", "PENDING_REFUND", "CANCELLED"),
                result.stream().map(r -> r.getDisplayStatus()).toList());
        assertEquals(f.records().stream().map(OrderReturn::getId).toList(), result.stream().map(r -> r.getId()).toList());
        verifyNoInteractions(inventory, payments, outbox, refundWriter);
    }

    @ParameterizedTest
    @CsvSource({"RECEIVED,PARTIALLY_REFUNDED", "RECEIVED,REFUNDED", "COMPLETED,REFUNDED"})
    void refundReplayReconcilesExactSettledTransactionWithoutSecondPaymentRequest(String state, String settled) {
        Fixture f = fixture(ReturnStatus.valueOf(state));
        OrderReturn record = f.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        UUID transaction = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID paymentId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        var ledger = new PaymentClient.PaymentRefundSummaryResponse(record.getId(), f.order().getId(), paymentId,
                transaction, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("300"), settled,
                "PARTIALLY_REFUNDED", "CASH", LocalDateTime.of(2026, 11, 3, 7, 0));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(ledger));
        if (record.getStatus() == ReturnStatus.RECEIVED) when(completion.isPhysicalDispositionCompleted(record.getId())).thenReturn(true);
        var response = OrderReturnRefundResponse.builder().returnId(record.getId()).refundTransactionId(transaction).build();
        when(refundWriter.applyRefundSuccess(eq(record.getId()), any(), any())).thenReturn(response);
        assertSame(response, service.refundReturn(record.getId()));
        assertSame(response, service.refundReturn(record.getId()));
        ArgumentCaptor<PaymentClient.PaymentRefundResponse> replay = ArgumentCaptor.forClass(PaymentClient.PaymentRefundResponse.class);
        verify(refundWriter, times(2)).applyRefundSuccess(eq(record.getId()), replay.capture(), any());
        assertTrue(replay.getAllValues().stream().allMatch(r -> transaction.equals(r.refundTransactionId())
                && paymentId.equals(r.paymentId()) && ledger.refundAmount().equals(r.refundAmount())));
        verify(payments, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(calculator, orders, outbox, inventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exceeds-remaining", "failed-ledger", "zero", "no-payment"})
    void refundExecutionRejectsInvalidCurrentLedgerBeforeSendingMutation(String invalid) {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        var ledger = new PaymentClient.PaymentRefundSummaryResponse(record.getId(), f.order().getId(),
                invalid.equals("no-payment") ? null : UUID.randomUUID(), null, null,
                new BigDecimal("250"), new BigDecimal("300"), invalid.equals("failed-ledger") ? "FAILED" : "NONE", "PAID", "CASH", null);
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(ledger));
        when(completion.isPhysicalDispositionCompleted(record.getId())).thenReturn(true);
        when(orders.findByIdAndIsDeletedFalse(f.order().getId())).thenReturn(Optional.of(f.order()));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(f.order().getId())).thenReturn(f.records());
        when(calculator.calculate(f.order(), record, f.records())).thenReturn(
                new ReturnRefundCalculator.RefundCalculationResult(invalid.equals("zero") ? BigDecimal.ZERO : new BigDecimal("100")));
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        assertThrows(InvalidOrderReturnException.class, () -> service.refundReturn(record.getId()));
        verify(payments, never()).refundOrderReturn(any(), any());
        verifyNoInteractions(refundWriter, outbox);
    }

    @Test
    void pendingCommandTimeoutCannotBeReportedAsRefundSuccessOrRetriedAsPayment() {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        var command = OrderReturnInventoryCommand.builder().commandId(UUID.randomUUID())
                .status(OrderReturnInventoryCommandStatus.PENDING).build();
        when(commands.findByOrderReturnIdWithLines(record.getId())).thenReturn(List.of(command));
        when(inventory.getReturnDispositionStatus(command.getCommandId())).thenThrow(transportFailure("timeout"));
        assertThrows(InvalidOrderReturnException.class, () -> service.refundReturn(record.getId()));
        verify(completion, times(2)).isPhysicalDispositionCompleted(record.getId());
        verifyNoInteractions(commandWriter, refundWriter, calculator, outbox, orders);
        verify(payments, never()).refundOrderReturn(any(), any());
    }

    @Test
    void refundCanReconcileStalePendingAckThenCallsPaymentExactlyOnceWithCurrentAmount() {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        when(payments.lookupRefundSummaries(any())).thenReturn(List.of(payment(f.rows().getFirst(), false)));
        when(completion.isPhysicalDispositionCompleted(record.getId())).thenReturn(false, true);
        var command = OrderReturnInventoryCommand.builder().commandId(UUID.randomUUID())
                .status(OrderReturnInventoryCommandStatus.PENDING).build();
        var failed = OrderReturnInventoryCommand.builder().commandId(UUID.randomUUID())
                .status(OrderReturnInventoryCommandStatus.FAILED).build();
        when(commands.findByOrderReturnIdWithLines(record.getId())).thenReturn(List.of(command, failed));
        var ack = ReturnDispositionStatusResponse.builder().commandId(command.getCommandId()).returnId(record.getId())
                .inspectionId(UUID.randomUUID()).status(ReturnDispositionReconciliationStatus.PROCESSED).processedAt(NOW).build();
        when(inventory.getReturnDispositionStatus(command.getCommandId())).thenReturn(ack);
        when(orders.findByIdAndIsDeletedFalse(f.order().getId())).thenReturn(Optional.of(f.order()));
        when(returns.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(f.order().getId())).thenReturn(f.records());
        when(calculator.calculate(f.order(), record, f.records())).thenReturn(new ReturnRefundCalculator.RefundCalculationResult(new BigDecimal("50")));
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        var paid = new PaymentClient.PaymentRefundResponse(UUID.randomUUID(), f.order().getId(), UUID.randomUUID(), record.getId(),
                new BigDecimal("50"), new BigDecimal("50"), "PARTIALLY_REFUNDED", "CASH", null);
        when(payments.refundOrderReturn(f.order().getId(), new PaymentClient.PaymentRefundRequest(record.getId(), new BigDecimal("50")))).thenReturn(paid);
        var expected = OrderReturnRefundResponse.builder().returnId(record.getId()).refundAmount(new BigDecimal("50")).build();
        when(refundWriter.applyRefundSuccess(eq(record.getId()), eq(paid), any())).thenReturn(expected);
        assertSame(expected, service.refundReturn(record.getId()));
        verify(commandWriter).reconcileCommand(OrderReturnInventoryCommandReconciliationWriter.CommandEvidence.from(ack));
        verify(inventory, never()).getReturnDispositionStatus(failed.getCommandId());
        verify(payments, times(1)).refundOrderReturn(f.order().getId(), new PaymentClient.PaymentRefundRequest(record.getId(), new BigDecimal("50")));
        var sequence = inOrder(inventory, commandWriter, payments, refundWriter);
        sequence.verify(inventory).getReturnDispositionStatus(command.getCommandId());
        sequence.verify(commandWriter).reconcileCommand(any());
        sequence.verify(payments).refundOrderReturn(any(), any());
        sequence.verify(refundWriter).applyRefundSuccess(eq(record.getId()), eq(paid), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void missingReturnOrNoRefundRequestStopsBeforeLookupAndTimelineKeepsAuditReferences() {
        UUID missing = UUID.randomUUID();
        assertThrows(OrderNotFoundException.class, () -> service.refundReturn(missing));
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        record.setRefundRequired(false);
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        assertThrows(InvalidOrderReturnException.class, () -> service.refundReturn(record.getId()));
        OrderReturnHistory audit = OrderReturnHistory.builder().action(OrderReturnHistoryAction.REFUND_COMPLETED)
                .fromStatus(ReturnStatus.RECEIVED).toStatus(ReturnStatus.COMPLETED).actorUserId(UUID.randomUUID())
                .note("Settled once").referenceType("PAYMENT_REFUND").referenceId("TX-FROZEN").occurredAt(NOW).build();
        audit.setId(UUID.randomUUID());
        when(histories.findByOrderReturnIdAndIsDeletedFalseOrderByOccurredAtAscIdAsc(record.getId())).thenReturn(List.of(audit));
        var timeline = service.getReturnTimeline(record.getId());
        assertEquals(1, timeline.size());
        assertEquals(OrderReturnHistoryAction.REFUND_COMPLETED, timeline.getFirst().getAction());
        assertEquals(audit.getActorUserId(), timeline.getFirst().getActorUserId());
        assertEquals("TX-FROZEN", timeline.getFirst().getReferenceId());
        assertEquals("PAYMENT_REFUND", timeline.getFirst().getReferenceType());
        assertEquals(NOW, timeline.getFirst().getOccurredAt());
        verifyNoInteractions(payments, inventory, outbox, refundWriter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-request", "no-command", "null-lines", "empty-lines"})
    void malformedDispositionEnvelopeCannotAcquireLockOrCreateCommand(String invalid) {
        SubmitReturnInventoryDispositionRequest request = SubmitReturnInventoryDispositionRequest.builder().commandId(UUID.randomUUID()).build();
        switch (invalid) {
            case "null-request" -> request = null;
            case "no-command" -> request.setCommandId(null);
            case "null-lines" -> request.setLines(null);
            case "empty-lines" -> request.setLines(List.of());
            default -> throw new AssertionError(invalid);
        }
        SubmitReturnInventoryDispositionRequest input = request;
        assertThrows(InvalidOrderReturnException.class, () -> service.submitInventoryDisposition(UUID.randomUUID(), input));
        verifyNoInteractions(returns, commands, inventory, payments, outbox, historyWriter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-line", "no-return-item", "no-result", "duplicate-line", "deleted-item", "null-items",
            "no-order-item", "no-sold-id", "no-variant", "null-quantity", "zero-quantity", "negative-quantity", "missing-destination"})
    void invalidDispositionLineCannotReserveQuantityOrPublishCommand(String invalid) {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        OrderItem sold = OrderItem.builder().productVariantId(UUID.randomUUID()).build();
        sold.setId(UUID.randomUUID());
        OrderReturnItem returned = OrderReturnItem.builder().orderItem(sold).quantity(new BigDecimal("3")).build();
        returned.setId(UUID.randomUUID());
        record.setItems(List.of(returned));
        var line = SubmitReturnInventoryDispositionLineRequest.builder().returnItemId(returned.getId())
                .quantity(BigDecimal.ONE).result(ReturnInventoryDispositionResult.RESTOCK).destinationLocationId(UUID.randomUUID()).build();
        switch (invalid) {
            case "null-line" -> line = null;
            case "no-return-item" -> line.setReturnItemId(null);
            case "no-result" -> line.setResult(null);
            case "deleted-item" -> returned.setIsDeleted(true);
            case "null-items" -> record.setItems(null);
            case "no-order-item" -> returned.setOrderItem(null);
            case "no-sold-id" -> sold.setId(null);
            case "no-variant" -> sold.setProductVariantId(null);
            case "null-quantity" -> line.setQuantity(null);
            case "zero-quantity" -> line.setQuantity(BigDecimal.ZERO);
            case "negative-quantity" -> line.setQuantity(new BigDecimal("-1"));
            case "missing-destination" -> line.setDestinationLocationId(null);
            case "duplicate-line" -> { }
            default -> throw new AssertionError(invalid);
        }
        var request = SubmitReturnInventoryDispositionRequest.builder().commandId(UUID.randomUUID())
                .lines(invalid.equals("duplicate-line") ? List.of(line, line) : Collections.singletonList(line)).build();
        when(returns.findByIdAndIsDeletedFalseForUpdate(record.getId())).thenReturn(Optional.of(record));
        assertThrows(InvalidOrderReturnException.class, () -> service.submitInventoryDisposition(record.getId(), request));
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(inventory, payments, outbox, historyWriter);
        assertEquals(ReturnStatus.RECEIVED, record.getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "no-parent", "no-return-id", "other-return"})
    void dispositionLookupRequiresExactReturnOwnership(String invalid) {
        UUID returnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        OrderReturn parent = new OrderReturn();
        parent.setId(invalid.equals("no-return-id") ? null : UUID.randomUUID());
        var command = OrderReturnInventoryCommand.builder().commandId(commandId)
                .orderReturn(invalid.equals("no-parent") ? null : parent).build();
        when(commands.findByCommandIdAndIsDeletedFalse(commandId)).thenReturn(invalid.equals("missing") ? Optional.empty() : Optional.of(command));
        assertThrows(OrderNotFoundException.class, () -> service.getInventoryDisposition(returnId, commandId));
        verify(commands, never()).saveAndFlush(any());
        verifyNoInteractions(returns, inventory, payments, outbox);
    }

    @ParameterizedTest
    @CsvSource({"detail,Inventory,401", "detail,Inventory,403", "detail,Inventory,400", "detail,Payment,401", "detail,Payment,403", "detail,Payment,400",
            "list,Inventory,401", "list,Inventory,403", "list,Inventory,400", "list,Payment,401", "list,Payment,403", "list,Payment,400"})
    void rawFeignAuthAndBusinessErrorsArePropagatedWithoutBeingMisreportedAsEmptyOrRefundable(String operation, String downstream, int status) {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        if (operation.equals("detail")) when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));
        else {
            when(returns.findRefundReturnSummaries(null, null, null)).thenReturn(f.rows());
            when(returns.findByIdInAndIsDeletedFalseWithItems(anyList())).thenReturn(f.records());
        }
        Request request = Request.create(Request.HttpMethod.GET, "http://unit.invalid/lookup", Map.of(), null, StandardCharsets.UTF_8, null);
        FeignException failure = FeignException.errorStatus("lookup", Response.builder().status(status).request(request).headers(Map.of()).build());
        if (downstream.equals("Inventory")) {
            if (operation.equals("detail")) when(inventory.getCustomerReturnQcSummary(record.getId())).thenThrow(failure);
            else when(inventory.getCustomerReturnQcSummaries(any())).thenThrow(failure);
        } else when(payments.lookupRefundSummaries(any())).thenThrow(failure);
        if (operation.equals("detail")) assertSame(failure, assertThrows(FeignException.class, () -> service.getRefundDetail(record.getId())));
        else assertSame(failure, assertThrows(FeignException.class, () -> service.getRefunds(null, null, null, null, null, null, 0, 10)));
        verifyNoInteractions(refundWriter, outbox, calculator);
        verify(orders, never()).save(any());
        verify(returns, never()).save(any());
        verify(payments, never()).refundOrderReturn(any(), any());
    }

    @Test
    void getReturnByIdMapsPendingAndFailedDispositionFlagsCorrectly() {
        Fixture f = fixture(ReturnStatus.RECEIVED);
        OrderReturn record = f.records().getFirst();
        when(returns.findByIdAndIsDeletedFalse(record.getId())).thenReturn(Optional.of(record));

        // Case 1: Pending command in flight
        when(progress.resolve(record)).thenReturn(physical(false, "PROCESSING", true, false));
        var res1 = service.getReturnById(record.getId());
        assertTrue(res1.getIsPendingDisposition());
        assertFalse(res1.getHasFailedDisposition());
        assertEquals("PROCESSING", res1.getDisplayStatus());

        // Case 2: Failed command (e.g. orphan recovery exhausted)
        when(progress.resolve(record)).thenReturn(physical(false, "FAILED", false, true));
        var res2 = service.getReturnById(record.getId());
        assertFalse(res2.getIsPendingDisposition());
        assertTrue(res2.getHasFailedDisposition());
        assertEquals("PROCESSING_FAILED", res2.getDisplayStatus());

        // Case 3: Completed physical disposition
        when(progress.resolve(record)).thenReturn(physical(true, "COMPLETED", false, false));
        var res3 = service.getReturnById(record.getId());
        assertFalse(res3.getIsPendingDisposition());
        assertFalse(res3.getHasFailedDisposition());
    }

    private void stubBatchProgress(Fixture fixture) {
        when(returns.findByIdInAndIsDeletedFalseWithItems(anyList())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return fixture.records().stream().filter(record -> ids.contains(record.getId())).toList();
        });
        when(progress.resolveBatch(anyCollection())).thenReturn(fixture.progress());
    }

    private Fixture fixture(ReturnStatus... states) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("ORDER-UNIT");
        order.setPaidAmount(new BigDecimal("300"));
        var records = new ArrayList<OrderReturn>();
        var rows = new ArrayList<OrderReturnSummaryResponse>();
        var physical = new HashMap<UUID, OrderReturnPhysicalProgress>();
        for (int i = 0; i < states.length; i++) {
            OrderReturn record = OrderReturn.builder().order(order).status(states[i]).refundRequired(true).items(List.of()).build();
            record.setId(UUID.randomUUID());
            record.setCreatedAt(NOW.plusSeconds(i));
            record.setReturnCode("RET-" + i);
            records.add(record);
            rows.add(OrderReturnSummaryResponse.builder().id(record.getId()).orderId(order.getId()).status(states[i])
                    .createdAt(record.getCreatedAt()).refundRequired(true).build());
            physical.put(record.getId(), OrderReturnPhysicalProgress.empty());
        }
        return new Fixture(order, records, rows, physical);
    }

    private OrderReturnPhysicalProgress physical(boolean done, String status, boolean pending, boolean failed) {
        return new OrderReturnPhysicalProgress(new BigDecimal("3"), done ? new BigDecimal("3") : BigDecimal.ZERO,
                done ? BigDecimal.ZERO : new BigDecimal("3"), done, status, pending, failed);
    }

    private PaymentClient.PaymentRefundSummaryResponse payment(OrderReturnSummaryResponse row, boolean settled) {
        return new PaymentClient.PaymentRefundSummaryResponse(row.getId(), row.getOrderId(), UUID.randomUUID(), settled ? UUID.randomUUID() : null,
                settled ? new BigDecimal("100") : null, BigDecimal.ZERO, new BigDecimal("300"), settled ? "REFUNDED" : "NONE", "PAID", "CASH", null);
    }

    private RuntimeException transportFailure(String type) {
        Request request = Request.create(Request.HttpMethod.GET, "http://unit.invalid/lookup", Map.of(), null, StandardCharsets.UTF_8, null);
        if ("timeout".equals(type)) return new RetryableException(-1, "unit timeout", Request.HttpMethod.GET, (Long) null, request);
        int status = type.equals("raw502") ? 502 : type.equals("raw504") ? 504 : 503;
        return FeignException.errorStatus("lookup", Response.builder().status(status).request(request).headers(Map.of()).build());
    }

    private record Fixture(Order order, List<OrderReturn> records, List<OrderReturnSummaryResponse> rows,
                           Map<UUID, OrderReturnPhysicalProgress> progress) {}
}
