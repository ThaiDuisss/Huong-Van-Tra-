package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.OrderReturnReportBaseRow;
import org.hvtra.orderservice.dto.response.OrderReturnReportCoreMetricsResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportDetailResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportQcBreakdownResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportReasonBreakdownResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnStatusBreakdownResponse;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderReturnReportServiceImpl implements OrderReturnReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.systemDefault();
    private static final int BATCH_SIZE = 100;
    private static final String REFUNDED = "REFUNDED";
    private static final String NOT_REFUNDED = "NOT_REFUNDED";

    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    @Override
    public OrderReturnReportSummaryResponse getSummary(
            UUID branchId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        Instant fromCreatedAt = startOfDay(fromDate);
        Instant toCreatedBefore = startOfDay(nextDay(toDate));
        OrderReturnReportCoreMetricsResponse metrics = orderReturnRepository.findReportCoreMetrics(
                branchId,
                fromCreatedAt,
                toCreatedBefore,
                ReturnStatus.COMPLETED,
                ReturnStatus.CANCELLED,
                List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED)
        );
        List<OrderReturnReportBaseRow> baseRows = orderReturnRepository.findReportBaseRows(
                null,
                null,
                branchId,
                fromCreatedAt,
                toCreatedBefore
        );
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> paymentByReturnId =
                lookupPaymentRefundSummaries(baseRows);
        List<PaymentClient.PaymentRefundSummaryResponse> refundedPayments = paymentByReturnId.values().stream()
                .filter(payment -> REFUNDED.equals(payment.refundStatus()))
                .toList();
        BigDecimal totalRefundAmount = refundedPayments.stream()
                .map(PaymentClient.PaymentRefundSummaryResponse::refundAmount)
                .map(this::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long refundedReturnCount = refundedPayments.size();
        BigDecimal averageRefundAmount = refundedReturnCount == 0
                ? BigDecimal.ZERO
                : totalRefundAmount.divide(BigDecimal.valueOf(refundedReturnCount), 0, RoundingMode.HALF_UP);

        BigDecimal returnedQuantity = defaultZero(orderReturnItemRepository.sumReportReturnedQuantity(
                branchId,
                fromCreatedAt,
                toCreatedBefore,
                List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED)
        ));

        return OrderReturnReportSummaryResponse.builder()
                .totalReturns(safeLong(metrics == null ? null : metrics.getTotalReturns()))
                .completedReturns(safeLong(metrics == null ? null : metrics.getCompletedReturns()))
                .processingReturns(safeLong(metrics == null ? null : metrics.getProcessingReturns()))
                .cancelledReturns(safeLong(metrics == null ? null : metrics.getCancelledReturns()))
                .ordersWithReturns(safeLong(metrics == null ? null : metrics.getOrdersWithReturns()))
                .returnedQuantity(returnedQuantity)
                .refundedReturnCount(refundedReturnCount)
                .totalRefundAmount(totalRefundAmount)
                .averageRefundAmount(averageRefundAmount)
                .statusBreakdown(statusBreakdown(branchId, fromCreatedAt, toCreatedBefore))
                .reasonBreakdown(reasonBreakdown(branchId, fromCreatedAt, toCreatedBefore))
                .qcBreakdown(qcBreakdown(baseRows))
                .build();
    }

    @Override
    public Page<OrderReturnReportDetailResponse> getDetails(
            String keyword,
            ReturnStatus returnStatus,
            String refundStatus,
            UUID branchId,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        List<OrderReturnReportBaseRow> baseRows = orderReturnRepository.findReportBaseRows(
                keywordPattern(keyword),
                returnStatus,
                branchId,
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        );
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> allPaymentsByReturnId =
                shouldFilterRefund(refundStatus) ? lookupPaymentRefundSummaries(baseRows) : Map.of();
        List<OrderReturnReportBaseRow> filteredRows = baseRows.stream()
                .filter(row -> refundStatusMatches(row, refundStatus, allPaymentsByReturnId.get(row.getReturnId())))
                .sorted(reportSort(sort))
                .toList();

        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 200);
        int fromIndex = Math.min(pageIndex * pageSize, filteredRows.size());
        int toIndex = Math.min(fromIndex + pageSize, filteredRows.size());
        List<OrderReturnReportBaseRow> pageRows = filteredRows.subList(fromIndex, toIndex);

        Map<UUID, OrderReturn> returnsById = lookupReturns(pageRows);
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> paymentByReturnId = shouldFilterRefund(refundStatus)
                ? allPaymentsByReturnId
                : lookupPaymentRefundSummaries(pageRows);
        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> qcByReturnId =
                lookupQcSummaries(pageRows, returnsById);

        List<OrderReturnReportDetailResponse> content = pageRows.stream()
                .map(row -> toDetailResponse(
                        row,
                        qcByReturnId.get(row.getReturnId()),
                        paymentByReturnId.get(row.getReturnId())
                ))
                .toList();

        return new PageImpl<>(
                content,
                PageRequest.of(pageIndex, pageSize),
                filteredRows.size()
        );
    }

    private List<OrderReturnStatusBreakdownResponse> statusBreakdown(
            UUID branchId,
            Instant fromCreatedAt,
            Instant toCreatedBefore
    ) {
        Map<ReturnStatus, Long> counts = orderReturnRepository
                .findReportStatusBreakdown(branchId, fromCreatedAt, toCreatedBefore)
                .stream()
                .collect(Collectors.toMap(
                        OrderReturnStatusBreakdownResponse::getStatus,
                        breakdown -> safeLong(breakdown.getCount()),
                        Long::sum,
                        () -> new EnumMap<>(ReturnStatus.class)
                ));

        return List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED, ReturnStatus.CANCELLED)
                .stream()
                .map(status -> new OrderReturnStatusBreakdownResponse(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    private List<OrderReturnReportReasonBreakdownResponse> reasonBreakdown(
            UUID branchId,
            Instant fromCreatedAt,
            Instant toCreatedBefore
    ) {
        Map<ReturnReason, OrderReturnReportReasonBreakdownResponse> byReason = orderReturnItemRepository
                .findReportReasonBreakdown(branchId, fromCreatedAt, toCreatedBefore, ReturnStatus.CANCELLED)
                .stream()
                .collect(Collectors.toMap(
                        OrderReturnReportReasonBreakdownResponse::getReason,
                        Function.identity(),
                        (first, second) -> first,
                        () -> new EnumMap<>(ReturnReason.class)
                ));

        return List.of(
                        ReturnReason.DEFECTIVE,
                        ReturnReason.DAMAGED,
                        ReturnReason.WRONG_ITEM,
                        ReturnReason.QUALITY_ISSUE,
                        ReturnReason.EXPIRED,
                        ReturnReason.CHANGE_OF_MIND,
                        ReturnReason.OTHER
                )
                .stream()
                .map(reason -> byReason.getOrDefault(
                        reason,
                        new OrderReturnReportReasonBreakdownResponse(reason, 0L, BigDecimal.ZERO)
                ))
                .toList();
    }

    private List<OrderReturnReportQcBreakdownResponse> qcBreakdown(List<OrderReturnReportBaseRow> baseRows) {
        List<UUID> returnIds = baseRows.stream()
                .filter(row -> row.getReturnStatus() != ReturnStatus.CANCELLED)
                .map(OrderReturnReportBaseRow::getReturnId)
                .toList();
        Map<String, BigDecimal> quantities = new java.util.HashMap<>();
        chunk(returnIds).forEach(chunk -> inventoryClient
                .getCustomerReturnQcDispositionReport(
                        new InventoryClient.CustomerReturnQcDispositionReportRequest(chunk)
                )
                .forEach(row -> quantities.merge(
                        "QUARANTINE".equals(row.result()) ? "DAMAGED" : row.result(),
                        defaultZero(row.quantity()),
                        BigDecimal::add
                )));

        BigDecimal total = quantities.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return List.of("RESTOCK", "DAMAGED", "WRITE_OFF").stream()
                .map(result -> {
                    BigDecimal quantity = quantities.getOrDefault(result, BigDecimal.ZERO);
                    BigDecimal percentage = total.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : quantity.multiply(BigDecimal.valueOf(100))
                                    .divide(total, 2, RoundingMode.HALF_UP);
                    return new OrderReturnReportQcBreakdownResponse(result, quantity, percentage);
                })
                .toList();
    }

    private Map<UUID, OrderReturn> lookupReturns(List<OrderReturnReportBaseRow> rows) {
        List<UUID> returnIds = rows.stream()
                .map(OrderReturnReportBaseRow::getReturnId)
                .toList();
        if (returnIds.isEmpty()) {
            return Map.of();
        }

        return orderReturnRepository.findByIdInAndIsDeletedFalseWithItems(returnIds)
                .stream()
                .collect(Collectors.toMap(OrderReturn::getId, Function.identity(), (first, second) -> first));
    }

    private Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> lookupQcSummaries(
            List<OrderReturnReportBaseRow> rows,
            Map<UUID, OrderReturn> returnsById
    ) {
        List<InventoryClient.CustomerReturnSnapshot> snapshots = rows.stream()
                .map(row -> returnsById.get(row.getReturnId()))
                .filter(orderReturn -> orderReturn != null)
                .map(this::toCustomerReturnSnapshot)
                .toList();
        if (snapshots.isEmpty()) {
            return Map.of();
        }

        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> results = new java.util.HashMap<>();
        chunk(snapshots).forEach(chunk -> inventoryClient
                .getCustomerReturnQcSummaries(new InventoryClient.CustomerReturnQcSummaryBatchRequest(chunk))
                .forEach(summary -> results.putIfAbsent(summary.returnId(), summary)));
        return results;
    }

    private InventoryClient.CustomerReturnSnapshot toCustomerReturnSnapshot(OrderReturn orderReturn) {
        Order order = orderReturn.getOrder();
        return new InventoryClient.CustomerReturnSnapshot(
                orderReturn.getId(),
                orderReturn.getReturnCode(),
                orderReturn.getStatus(),
                order.getBranchId(),
                order.getId(),
                order.getOrderCode(),
                orderReturn.getItems().stream()
                        .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                        .map(this::toCustomerReturnLineSnapshot)
                        .toList()
        );
    }

    private InventoryClient.CustomerReturnLineSnapshot toCustomerReturnLineSnapshot(OrderReturnItem item) {
        OrderItem orderItem = item.getOrderItem();
        return new InventoryClient.CustomerReturnLineSnapshot(
                item.getId(),
                orderItem == null ? null : orderItem.getId(),
                orderItem == null ? null : orderItem.getProductVariantId(),
                item.getQuantity(),
                item.getCondition(),
                item.getReason() == null ? null : item.getReason().name()
        );
    }

    private Map<UUID, PaymentClient.PaymentRefundSummaryResponse> lookupPaymentRefundSummaries(
            List<OrderReturnReportBaseRow> rows
    ) {
        List<PaymentClient.RefundSummaryLookupItem> items = rows.stream()
                .map(row -> new PaymentClient.RefundSummaryLookupItem(row.getReturnId(), row.getOrderId()))
                .toList();
        if (items.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> results = new java.util.HashMap<>();
        chunk(items).forEach(chunk -> paymentClient
                .lookupRefundSummaries(new PaymentClient.RefundSummaryLookupRequest(chunk))
                .forEach(summary -> results.putIfAbsent(summary.returnId(), summary)));
        return results;
    }

    private OrderReturnReportDetailResponse toDetailResponse(
            OrderReturnReportBaseRow row,
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        boolean refunded = paymentSummary != null && REFUNDED.equals(paymentSummary.refundStatus());
        return OrderReturnReportDetailResponse.builder()
                .returnId(row.getReturnId())
                .returnCode(row.getReturnCode())
                .orderId(row.getOrderId())
                .orderCode(row.getOrderCode())
                .branchId(row.getBranchId())
                .customerName(row.getCustomerName())
                .customerPhone(row.getCustomerPhone())
                .returnStatus(row.getReturnStatus())
                .qcStatus(qcSummary == null ? "PENDING" : qcSummary.qcStatus())
                .refundAmount(refunded ? defaultZero(paymentSummary.refundAmount()) : BigDecimal.ZERO)
                .method(paymentSummary == null ? null : paymentSummary.method())
                .paymentStatus(paymentSummary == null ? null : paymentSummary.paymentStatus())
                .refundStatus(refunded ? REFUNDED : NOT_REFUNDED)
                .returnCreatedAt(row.getReturnCreatedAt())
                .refundedAt(refunded ? paymentSummary.refundedAt() : null)
                .build();
    }

    private boolean refundStatusMatches(
            OrderReturnReportBaseRow row,
            String refundStatus,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        if (!shouldFilterRefund(refundStatus)) {
            return true;
        }
        boolean refunded = paymentSummary != null && REFUNDED.equals(paymentSummary.refundStatus());
        if (REFUNDED.equals(refundStatus)) {
            return refunded;
        }
        if (NOT_REFUNDED.equals(refundStatus)) {
            return !refunded || row.getReturnStatus() == ReturnStatus.CANCELLED;
        }
        return true;
    }

    private boolean shouldFilterRefund(String refundStatus) {
        return REFUNDED.equals(refundStatus) || NOT_REFUNDED.equals(refundStatus);
    }

    private Comparator<OrderReturnReportBaseRow> reportSort(String sort) {
        boolean ascending = sort != null && sort.toLowerCase().contains("asc");
        Comparator<OrderReturnReportBaseRow> comparator = Comparator
                .comparing(
                        OrderReturnReportBaseRow::getReturnCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(OrderReturnReportBaseRow::getReturnId);
        return ascending ? comparator : comparator.reversed();
    }

    private String keywordPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }

    private Instant startOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private LocalDate nextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<List<T>> chunk(List<T> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int index = 0; index < items.size(); index += BATCH_SIZE) {
            chunks.add(items.subList(index, Math.min(index + BATCH_SIZE, items.size())));
        }
        return chunks;
    }
}
