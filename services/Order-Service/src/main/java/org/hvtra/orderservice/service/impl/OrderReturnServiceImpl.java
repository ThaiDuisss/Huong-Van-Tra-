package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderRefundDetailResponse;
import org.hvtra.orderservice.dto.response.OrderRefundStatsResponse;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundPreviewResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private static final List<ReturnStatus> ACTIVE_RETURN_STATUSES =
            List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
    private static final Duration DEFAULT_CHANGE_OF_MIND_WINDOW = Duration.ofDays(7);
    private static final Duration DEFAULT_QUALITY_WINDOW = Duration.ofDays(14);

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderReturnMapper orderReturnMapper;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ReturnRefundCalculator returnRefundCalculator;

    @Override
    @Transactional
    public OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        validateOrderEligibility(order);
        validateRequest(request);

        Map<UUID, OrderItem> orderItems = order.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        OrderReturn orderReturn = OrderReturn.builder()
                .returnCode(generateUniqueReturnCode())
                .order(order)
                .status(ReturnStatus.CREATED)
                .note(normalizeBlank(request.getNote()))
                .build();

        Instant completedAt = resolveCompletedAt(order);
        for (CreateOrderReturnItemRequest itemRequest : request.getItems()) {
            OrderItem orderItem = orderItems.get(itemRequest.getOrderItemId());
            if (orderItem == null) {
                throw new InvalidOrderReturnException("Order item does not belong to order");
            }

            validateItemRules(itemRequest, completedAt);
            validateReturnQuantity(orderItem, itemRequest.getQuantity());

            orderReturn.addItem(OrderReturnItem.builder()
                    .orderItem(orderItem)
                    .quantity(itemRequest.getQuantity())
                    .reason(itemRequest.getReason())
                    .condition(itemRequest.getCondition())
                    .note(normalizeBlank(itemRequest.getNote()))
                    .build());
        }

        return orderReturnMapper.toResponse(orderReturnRepository.save(orderReturn));
    }

    @Override
    @Transactional
    public OrderReturnResponse receiveReturn(UUID returnId) {
        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        if (ReturnStatus.CREATED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only created returns can be received");
        }

        orderReturn.setStatus(ReturnStatus.RECEIVED);
        return orderReturnMapper.toResponse(orderReturnRepository.save(orderReturn));
    }

    @Override
    @Transactional
    public OrderReturnResponse cancelReturn(UUID returnId) {
        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        if (ReturnStatus.CREATED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only created returns can be cancelled");
        }

        orderReturn.setStatus(ReturnStatus.CANCELLED);
        return orderReturnMapper.toResponse(orderReturnRepository.save(orderReturn));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderRefundSummaryResponse> getRefunds(
            String keyword,
            OrderRefundStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        List<OrderReturnSummaryResponse> summaries = orderReturnRepository.findRefundReturnSummaries(
                keywordPattern(keyword),
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        );
        List<OrderRefundSummaryResponse> refundRows = buildRefundSummaries(summaries);
        List<OrderRefundSummaryResponse> filteredRows = refundRows.stream()
                .filter(row -> status == null || row.getRefundStatus() == status)
                .sorted(refundSort(sort))
                .toList();

        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 200);
        int fromIndex = Math.min(pageIndex * pageSize, filteredRows.size());
        int toIndex = Math.min(fromIndex + pageSize, filteredRows.size());
        return new PageImpl<>(
                filteredRows.subList(fromIndex, toIndex),
                PageRequest.of(pageIndex, pageSize),
                filteredRows.size()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrderRefundDetailResponse getRefundDetail(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
        InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                inventoryClient.getCustomerReturnQcSummary(returnId);
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = lookupPaymentRefundSummary(orderReturn);
        OrderRefundStatus refundStatus = resolveRefundStatus(orderReturn, qcSummary, paymentSummary);

        return OrderRefundDetailResponse.builder()
                .returnId(orderReturn.getId())
                .returnCode(orderReturn.getReturnCode())
                .orderId(orderReturn.getOrder().getId())
                .orderCode(orderReturn.getOrder().getOrderCode())
                .customerName(orderReturn.getOrder().getCustomerNameSnapshot())
                .customerPhone(orderReturn.getOrder().getCustomerPhoneSnapshot())
                .returnCreatedAt(orderReturn.getCreatedAt())
                .returnStatus(orderReturn.getStatus())
                .qcStatus(qcSummary == null ? null : qcSummary.qcStatus())
                .refundStatus(refundStatus)
                .refundAmount(paymentSummary == null ? null : paymentSummary.refundAmount())
                .totalAmountRefunded(paymentSummary == null ? null : paymentSummary.totalAmountRefunded())
                .paymentStatus(paymentSummary == null ? null : paymentSummary.paymentStatus())
                .method(paymentSummary == null ? null : paymentSummary.method())
                .refundedAt(paymentSummary == null ? null : paymentSummary.refundedAt())
                .items(orderReturnMapper.toResponse(orderReturn).getItems())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnRefundPreviewResponse getRefundPreview(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
        InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                inventoryClient.getCustomerReturnQcSummary(returnId);
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = lookupPaymentRefundSummary(orderReturn);

        String ineligibleReason = refundIneligibleReason(orderReturn, qcSummary, paymentSummary);
        if (ineligibleReason != null) {
            return OrderReturnRefundPreviewResponse.builder()
                    .returnId(returnId)
                    .orderId(orderReturn.getOrder().getId())
                    .paymentMethod(paymentSummary == null ? null : paymentSummary.method())
                    .eligible(false)
                    .reason(ineligibleReason)
                    .build();
        }

        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(
                        orderReturn.getOrder().getId()
                )
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));
        List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(
                lockedOrder.getId()
        );
        BigDecimal refundAmount = returnRefundCalculator
                .calculate(lockedOrder, orderReturn, orderReturns)
                .refundAmount();

        return OrderReturnRefundPreviewResponse.builder()
                .returnId(returnId)
                .orderId(lockedOrder.getId())
                .refundAmount(refundAmount)
                .paymentMethod(paymentSummary == null ? null : paymentSummary.method())
                .eligible(refundAmount.compareTo(BigDecimal.ZERO) > 0)
                .reason(refundAmount.compareTo(BigDecimal.ZERO) > 0 ? null : "Refund amount must be greater than 0")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderRefundStatsResponse getRefundStats(String keyword, LocalDate fromDate, LocalDate toDate) {
        List<OrderRefundSummaryResponse> rows = buildRefundSummaries(orderReturnRepository.findRefundReturnSummaries(
                keywordPattern(keyword),
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        ));

        return OrderRefundStatsResponse.builder()
                .total(rows.size())
                .pendingRefund(rows.stream().filter(row -> row.getRefundStatus() == OrderRefundStatus.PENDING).count())
                .refunded(rows.stream().filter(row -> row.getRefundStatus() == OrderRefundStatus.REFUNDED).count())
                .notReady(rows.stream().filter(row -> row.getRefundStatus() == OrderRefundStatus.NOT_READY).count())
                .failed(rows.stream().filter(row -> row.getRefundStatus() == OrderRefundStatus.FAILED).count())
                .build();
    }

    @Override
    @Transactional
    public OrderReturnRefundResponse refundReturn(UUID returnId) {
        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        if (ReturnStatus.COMPLETED == orderReturn.getStatus()) {
            return existingCompletedRefundResponse(orderReturn);
        }
        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Return inventory inspection is not completed");
        }

        InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                inventoryClient.getCustomerReturnQcSummary(returnId);
        if (qcSummary == null || !"COMPLETED".equals(qcSummary.qcStatus())) {
            throw new InvalidOrderReturnException("Return inventory inspection is not completed");
        }

        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(
                        orderReturn.getOrder().getId()
                )
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));
        List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(
                lockedOrder.getId()
        );
        BigDecimal refundAmount = returnRefundCalculator
                .calculate(lockedOrder, orderReturn, orderReturns)
                .refundAmount();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderReturnException("Refund amount must be greater than 0");
        }

        PaymentClient.PaymentRefundResponse paymentRefund = paymentClient.refundOrderReturn(
                lockedOrder.getId(),
                new PaymentClient.PaymentRefundRequest(returnId, refundAmount)
        );

        finalizeReturnAfterRefund(orderReturn, lockedOrder, orderReturns, paymentRefund);

        orderReturnRepository.save(orderReturn);
        orderRepository.save(lockedOrder);

        return toRefundResponse(orderReturn, lockedOrder, paymentRefund);
    }

    private OrderReturnRefundResponse existingCompletedRefundResponse(OrderReturn orderReturn) {
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = lookupPaymentRefundSummary(orderReturn);
        if (paymentSummary == null || !"REFUNDED".equals(paymentSummary.refundStatus())) {
            throw new InvalidOrderReturnException("Return already completed without a refund record");
        }
        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(
                        orderReturn.getOrder().getId()
                )
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));
        lockedOrder.setReturnStatus(resolveOrderReturnStatus(
                lockedOrder,
                orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(lockedOrder.getId())
        ));
        PaymentStatus paymentStatus = parsePaymentStatus(paymentSummary.paymentStatus());
        if (paymentStatus == PaymentStatus.PARTIALLY_REFUNDED || paymentStatus == PaymentStatus.REFUNDED) {
            lockedOrder.setPaymentStatus(paymentStatus);
        }
        orderRepository.save(lockedOrder);

        return OrderReturnRefundResponse.builder()
                .returnId(orderReturn.getId())
                .orderId(lockedOrder.getId())
                .paymentId(paymentSummary.paymentId())
                .refundTransactionId(paymentSummary.refundTransactionId())
                .refundAmount(paymentSummary.refundAmount())
                .totalAmountRefunded(paymentSummary.totalAmountRefunded())
                .paymentStatus(paymentSummary.paymentStatus())
                .returnStatus(orderReturn.getStatus())
                .orderPaymentStatus(lockedOrder.getPaymentStatus())
                .orderReturnStatus(lockedOrder.getReturnStatus())
                .method(paymentSummary.method())
                .refundedAt(paymentSummary.refundedAt())
                .build();
    }

    private void finalizeReturnAfterRefund(
            OrderReturn orderReturn,
            Order lockedOrder,
            List<OrderReturn> orderReturns,
            PaymentClient.PaymentRefundResponse paymentRefund
    ) {
        PaymentStatus paymentStatus = parsePaymentStatus(paymentRefund.paymentStatus());
        if (paymentStatus != PaymentStatus.PARTIALLY_REFUNDED && paymentStatus != PaymentStatus.REFUNDED) {
            throw new InvalidOrderReturnException("Payment refund is not completed");
        }

        if (ReturnStatus.RECEIVED == orderReturn.getStatus()) {
            orderReturn.setStatus(ReturnStatus.COMPLETED);
        }
        lockedOrder.setPaymentStatus(paymentStatus);
        lockedOrder.setReturnStatus(resolveOrderReturnStatus(lockedOrder, orderReturns));
    }

    private OrderReturnRefundResponse toRefundResponse(
            OrderReturn orderReturn,
            Order order,
            PaymentClient.PaymentRefundResponse paymentRefund
    ) {
        return OrderReturnRefundResponse.builder()
                .returnId(orderReturn.getId())
                .orderId(order.getId())
                .paymentId(paymentRefund.paymentId())
                .refundTransactionId(paymentRefund.refundTransactionId())
                .refundAmount(paymentRefund.refundAmount())
                .totalAmountRefunded(paymentRefund.totalAmountRefunded())
                .paymentStatus(paymentRefund.paymentStatus())
                .returnStatus(orderReturn.getStatus())
                .orderPaymentStatus(order.getPaymentStatus())
                .orderReturnStatus(order.getReturnStatus())
                .method(paymentRefund.method())
                .refundedAt(paymentRefund.refundedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return orderReturnRepository.findReturnSummaries(
                keywordPattern(keyword),
                status,
                startOfDay(fromDate),
                startOfDay(nextDay(toDate)),
                returnPageRequest(page, size, sort)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderReturnResponse> getReturnsByOrder(UUID orderId) {
        if (!orderRepository.findByIdAndIsDeletedFalse(orderId).isPresent()) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        return orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId)
                .stream()
                .map(orderReturnMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getReturnById(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
        return orderReturnMapper.toResponse(orderReturn);
    }

    private OrderReturn getExistingReturnForUpdate(UUID returnId) {
        return orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
    }

    private List<OrderRefundSummaryResponse> buildRefundSummaries(List<OrderReturnSummaryResponse> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }

        List<UUID> returnIds = summaries.stream()
                .map(OrderReturnSummaryResponse::getId)
                .toList();
        Map<UUID, OrderReturn> returnsById = orderReturnRepository
                .findByIdInAndIsDeletedFalseWithItems(returnIds)
                .stream()
                .collect(Collectors.toMap(OrderReturn::getId, Function.identity()));
        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> qcByReturnId =
                lookupQcSummaries(summaries, returnsById);
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> paymentByReturnId =
                lookupPaymentRefundSummaries(summaries);

        return summaries.stream()
                .map(summary -> {
                    OrderReturn orderReturn = returnsById.get(summary.getId());
                    InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcByReturnId.get(summary.getId());
                    PaymentClient.PaymentRefundSummaryResponse paymentSummary = paymentByReturnId.get(summary.getId());
                    OrderRefundStatus refundStatus = orderReturn == null
                            ? OrderRefundStatus.NOT_READY
                            : resolveRefundStatus(orderReturn, qcSummary, paymentSummary);

                    return OrderRefundSummaryResponse.builder()
                            .returnId(summary.getId())
                            .returnCode(summary.getReturnCode())
                            .orderId(summary.getOrderId())
                            .orderCode(summary.getOrderCode())
                            .customerName(summary.getCustomerName())
                            .customerPhone(summary.getCustomerPhone())
                            .returnCreatedAt(summary.getCreatedAt())
                            .returnStatus(summary.getStatus())
                            .qcStatus(qcSummary == null ? null : qcSummary.qcStatus())
                            .refundStatus(refundStatus)
                            .refundAmount(paymentSummary == null ? null : paymentSummary.refundAmount())
                            .paymentStatus(paymentSummary == null ? null : paymentSummary.paymentStatus())
                            .method(paymentSummary == null ? null : paymentSummary.method())
                            .refundedAt(paymentSummary == null ? null : paymentSummary.refundedAt())
                            .build();
                })
                .toList();
    }

    private Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> lookupQcSummaries(
            List<OrderReturnSummaryResponse> summaries,
            Map<UUID, OrderReturn> returnsById
    ) {
        List<InventoryClient.CustomerReturnSnapshot> snapshots = summaries.stream()
                .map(summary -> returnsById.get(summary.getId()))
                .filter(orderReturn -> orderReturn != null)
                .map(this::toCustomerReturnSnapshot)
                .toList();
        if (snapshots.isEmpty()) {
            return Map.of();
        }

        return inventoryClient.getCustomerReturnQcSummaries(
                        new InventoryClient.CustomerReturnQcSummaryBatchRequest(snapshots)
                )
                .stream()
                .collect(Collectors.toMap(
                        InventoryClient.CustomerReturnQcSummaryResponse::returnId,
                        Function.identity(),
                        (first, second) -> first
                ));
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
            List<OrderReturnSummaryResponse> summaries
    ) {
        List<PaymentClient.RefundSummaryLookupItem> items = summaries.stream()
                .map(summary -> new PaymentClient.RefundSummaryLookupItem(summary.getId(), summary.getOrderId()))
                .toList();
        if (items.isEmpty()) {
            return Map.of();
        }

        return paymentClient.lookupRefundSummaries(new PaymentClient.RefundSummaryLookupRequest(items))
                .stream()
                .collect(Collectors.toMap(
                        PaymentClient.PaymentRefundSummaryResponse::returnId,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    private PaymentClient.PaymentRefundSummaryResponse lookupPaymentRefundSummary(OrderReturn orderReturn) {
        return paymentClient.lookupRefundSummaries(new PaymentClient.RefundSummaryLookupRequest(List.of(
                        new PaymentClient.RefundSummaryLookupItem(
                                orderReturn.getId(),
                                orderReturn.getOrder().getId()
                        )
                )))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private OrderRefundStatus resolveRefundStatus(
            OrderReturn orderReturn,
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
            return OrderRefundStatus.REFUNDED;
        }
        if (paymentSummary != null && "FAILED".equals(paymentSummary.refundStatus())) {
            return OrderRefundStatus.FAILED;
        }
        if (ReturnStatus.RECEIVED == orderReturn.getStatus()
                && qcSummary != null
                && "COMPLETED".equals(qcSummary.qcStatus())) {
            return OrderRefundStatus.PENDING;
        }
        return OrderRefundStatus.NOT_READY;
    }

    private OrderReturnStatus resolveOrderReturnStatus(Order order, List<OrderReturn> orderReturns) {
        Map<UUID, BigDecimal> completedReturnedQuantities = orderReturns.stream()
                .filter(returnRecord -> ReturnStatus.COMPLETED == returnRecord.getStatus())
                .flatMap(returnRecord -> (returnRecord.getItems() == null
                        ? List.<OrderReturnItem>of()
                        : returnRecord.getItems()).stream())
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .filter(item -> item.getOrderItem() != null && item.getOrderItem().getId() != null)
                .collect(Collectors.toMap(
                        item -> item.getOrderItem().getId(),
                        item -> defaultZero(item.getQuantity()),
                        BigDecimal::add
                ));

        boolean anyReturned = completedReturnedQuantities.values().stream()
                .anyMatch(quantity -> quantity.compareTo(BigDecimal.ZERO) > 0);
        if (!anyReturned) {
            return OrderReturnStatus.NONE;
        }

        boolean fullyReturned = order.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .allMatch(item -> defaultZero(completedReturnedQuantities.get(item.getId()))
                        .compareTo(defaultZero(item.getQuantity())) >= 0);
        return fullyReturned ? OrderReturnStatus.FULLY_RETURNED : OrderReturnStatus.PARTIALLY_RETURNED;
    }

    private PaymentStatus parsePaymentStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderReturnException("Payment refund is not completed");
        }
        try {
            return PaymentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidOrderReturnException("Payment refund is not completed");
        }
    }

    private String refundIneligibleReason(
            OrderReturn orderReturn,
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
            return "Return already refunded";
        }
        if (ReturnStatus.RECEIVED != orderReturn.getStatus()
                || qcSummary == null
                || !"COMPLETED".equals(qcSummary.qcStatus())) {
            return "Return inventory inspection is not completed";
        }
        if (paymentSummary == null || paymentSummary.paymentId() == null) {
            return "Payment not found";
        }
        if (!"CASH".equals(paymentSummary.method())) {
            return "Refund method is not supported yet";
        }
        return null;
    }

    private Comparator<OrderRefundSummaryResponse> refundSort(String sort) {
        Comparator<OrderRefundSummaryResponse> comparator = Comparator
                .comparing(
                        OrderRefundSummaryResponse::getReturnCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        row -> row.getReturnId() == null ? "" : row.getReturnId().toString()
                );

        boolean ascending = sort != null && sort.toLowerCase(Locale.ROOT).contains("asc");
        return ascending ? comparator : comparator.reversed();
    }

    private void validateOrderEligibility(Order order) {
        if (OrderStatus.COMPLETED != order.getStatus()
                || (PaymentStatus.PAID != order.getPaymentStatus()
                && PaymentStatus.PARTIALLY_REFUNDED != order.getPaymentStatus())) {
            throw new InvalidOrderReturnException("Order is not eligible for return");
        }
    }

    private void validateRequest(CreateOrderReturnRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidOrderReturnException("Return items are required");
        }

        HashSet<UUID> orderItemIds = new HashSet<>();
        for (CreateOrderReturnItemRequest item : request.getItems()) {
            if (item.getOrderItemId() == null) {
                throw new InvalidOrderReturnException("Order item is required");
            }
            if (!orderItemIds.add(item.getOrderItemId())) {
                throw new InvalidOrderReturnException("Duplicate order item in return request");
            }
        }
    }

    private void validateItemRules(CreateOrderReturnItemRequest itemRequest, Instant completedAt) {
        requirePositiveQuantity(itemRequest.getQuantity());
        if (itemRequest.getReason() == null) {
            throw new InvalidOrderReturnException("Return reason is required");
        }
        if (itemRequest.getCondition() == null) {
            throw new InvalidOrderReturnException("Return condition is required");
        }
        if (ReturnReason.OTHER == itemRequest.getReason() && isBlank(itemRequest.getNote())) {
            throw new InvalidOrderReturnException("Note is required when reason is OTHER");
        }
        if (ReturnReason.CHANGE_OF_MIND == itemRequest.getReason()
                && ReturnCondition.SEALED != itemRequest.getCondition()) {
            throw new InvalidOrderReturnException("Change-of-mind return requires sealed product");
        }
        validateReturnWindow(itemRequest.getReason(), completedAt);
    }

    private void validateReturnQuantity(OrderItem orderItem, BigDecimal requestedQuantity) {
        BigDecimal purchasedQuantity = requirePositiveQuantity(orderItem.getQuantity());
        BigDecimal returnedQuantity = defaultZero(orderReturnItemRepository.sumReturnedQuantity(
                orderItem.getId(),
                ACTIVE_RETURN_STATUSES
        ));
        BigDecimal remainingQuantity = purchasedQuantity.subtract(returnedQuantity);

        if (requestedQuantity.compareTo(remainingQuantity) > 0) {
            throw new InvalidOrderReturnException("Return quantity exceeds remaining returnable quantity");
        }
    }

    private void validateReturnWindow(ReturnReason reason, Instant completedAt) {
        Instant baseline = completedAt == null ? Instant.now() : completedAt;
        Duration window = ReturnReason.CHANGE_OF_MIND == reason
                ? DEFAULT_CHANGE_OF_MIND_WINDOW
                : DEFAULT_QUALITY_WINDOW;

        if (baseline.plus(window).isBefore(Instant.now())) {
            throw new InvalidOrderReturnException("Return window has expired");
        }
    }

    private Instant resolveCompletedAt(Order order) {
        return orderStatusHistoryRepository
                .findFirstByOrderIdAndToStatusAndIsDeletedFalseOrderByChangedAtDesc(
                        order.getId(),
                        OrderStatus.COMPLETED
                )
                .map(history -> history.getChangedAt() == null ? null : history.getChangedAt())
                .orElseGet(() -> order.getUpdatedAt() == null ? order.getCreatedAt() : order.getUpdatedAt());
    }

    private BigDecimal requirePositiveQuantity(BigDecimal quantity) {
        BigDecimal normalized = defaultZero(quantity);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderReturnException("Return quantity must be greater than 0");
        }
        return normalized;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateUniqueReturnCode() {
        String returnCode = orderCodeGenerator.generateReturnCode();
        while (orderReturnRepository.existsByReturnCode(returnCode)) {
            returnCode = orderCodeGenerator.generateReturnCode();
        }
        return returnCode;
    }

    private String normalizeBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String keywordPattern(String keyword) {
        if (isBlank(keyword)) {
            return null;
        }

        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest returnPageRequest(int page, int size, String sort) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                returnSort(sort)
        );
    }

    private Sort returnSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }

        String[] parts = sort.trim().split(",", 2);
        String property = normalizeReturnSortProperty(parts[0]);
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(new Sort.Order(direction, property), Sort.Order.desc("id"));
    }

    private String normalizeReturnSortProperty(String property) {
        if (property == null || property.isBlank()) {
            return "createdAt";
        }

        return switch (property.trim()) {
            case "updatedAt" -> "updatedAt";
            case "returnCode" -> "returnCode";
            default -> "createdAt";
        };
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private LocalDate nextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
