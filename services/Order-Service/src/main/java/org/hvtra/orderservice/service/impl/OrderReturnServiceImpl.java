package org.hvtra.orderservice.service.impl;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommandLine;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderRefundBlockReason;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.request.CheckOrderReturnEligibilityRequest;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityResponse;
import org.hvtra.orderservice.service.support.OrderReturnCreationEligibilityResolver;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionLineRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.response.OrderRefundDetailResponse;
import org.hvtra.orderservice.dto.response.OrderRefundStatsResponse;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnInventoryDispositionLineResponse;
import org.hvtra.orderservice.dto.response.OrderReturnInventoryDispositionResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundPreviewResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnTimelineResponse;
import org.hvtra.orderservice.dto.response.ReturnDispositionReconciliationStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderItemRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.service.OrderReturnRefundTransactionService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnDisplayStatusResolver;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalCompletionHandler;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgress;
import org.hvtra.orderservice.service.support.OrderReturnPhysicalProgressResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver;
import org.hvtra.orderservice.service.support.OrderReturnRefundEligibilityResolver.RefundEligibilityResult;
import org.hvtra.orderservice.service.support.RefundProgressStatusResolver;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private static final List<ReturnStatus> ACTIVE_RETURN_STATUSES =
            List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
    private static final String DOWNSTREAM_STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String DOWNSTREAM_METHOD_UNKNOWN = "UNKNOWN";

    private static int getReturnPriority(OrderReturnSummaryResponse row) {
        if (row.getStatus() == ReturnStatus.CREATED) {
            return 4;
        }
        if ("PROCESSING_FAILED".equalsIgnoreCase(row.getDisplayStatus())
                || Boolean.TRUE.equals(row.getHasFailedDisposition())) {
            return 1;
        }
        if (Boolean.TRUE.equals(row.getIsPendingDisposition())) {
            return 2;
        }
        if (row.getStatus() == ReturnStatus.CANCELLED
                || "CANCELLED".equalsIgnoreCase(row.getDisplayStatus())) {
            return 7;
        }
        if (row.getStatus() == ReturnStatus.COMPLETED
                || "COMPLETED".equalsIgnoreCase(row.getDisplayStatus())) {
            return 6;
        }
        if ("PENDING_REFUND".equalsIgnoreCase(row.getDisplayStatus())) {
            return 5;
        }
        if (row.getStatus() == ReturnStatus.RECEIVED) {
            if ("COMPLETED".equalsIgnoreCase(row.getQcStatus())
                    || Boolean.TRUE.equals(row.getPhysicalDone())) {
                return 5;
            }
            BigDecimal processed = row.getProcessedQuantity() != null
                    ? row.getProcessedQuantity() : row.getPostedQcQuantity();
            BigDecimal remaining = row.getRemainingQuantity() != null
                    ? row.getRemainingQuantity() : row.getRemainingQcQuantity();
            boolean partiallyProcessed = processed != null && processed.signum() > 0
                    && remaining != null && remaining.signum() > 0;
            if ("PARTIAL".equalsIgnoreCase(row.getQcStatus()) || partiallyProcessed) {
                return 2;
            }
            return 3;
        }
        return 99;
    }

    private static final Comparator<OrderReturnSummaryResponse> RETURN_PRIORITY_COMPARATOR = Comparator
            .comparingInt(OrderReturnServiceImpl::getReturnPriority)
            .thenComparing(OrderReturnSummaryResponse::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(OrderReturnSummaryResponse::getId, Comparator.nullsLast(Comparator.reverseOrder()));


    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;
    private final OrderReturnHistoryRepository orderReturnHistoryRepository;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderReturnMapper orderReturnMapper;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ReturnRefundCalculator returnRefundCalculator;
    private final OrderReturnHistoryWriter orderReturnHistoryWriter;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderReturnRefundTransactionService orderReturnRefundTransactionService;
    private final OrderReturnInventoryCommandReconciliationWriter inventoryCommandReconciliationWriter;
    private final OrderReturnPhysicalProgressResolver physicalProgressResolver;
    private final OrderReturnDisplayStatusResolver displayStatusResolver;
    private final OrderReturnRefundEligibilityResolver refundEligibilityResolver;
    private final OrderReturnPhysicalCompletionHandler physicalCompletionHandler;
    private final OrderReturnCreationEligibilityResolver creationEligibilityResolver;

    @Override
    @Transactional(readOnly = true)
    public OrderReturnEligibilityResponse checkReturnEligibility(UUID orderId, CheckOrderReturnEligibilityRequest request) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItems(orderId).orElse(null);
        CreateOrderReturnRequest draft = CreateOrderReturnRequest.builder()
                .items(request == null ? null : request.items())
                .build();
        Map<UUID, OrderItem> items = order == null ? Map.of() : order.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        return creationEligibilityResolver.resolve(order, draft, items, returnedQuantities(items, draft));
    }

    private Map<UUID, BigDecimal> returnedQuantities(Map<UUID, OrderItem> items, CreateOrderReturnRequest request) {
        if (request == null || request.getItems() == null) {
            return Map.of();
        }
        return request.getItems().stream()
                .filter(Objects::nonNull)
                .map(CreateOrderReturnItemRequest::getOrderItemId)
                .filter(Objects::nonNull)
                .filter(items::containsKey)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), id -> defaultZero(
                        orderReturnItemRepository.sumReturnedQuantity(id, ACTIVE_RETURN_STATUSES))));
    }

    @Override
    @Transactional
    public OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        creationEligibilityResolver.requireValidOrderAndRequest(order, request);

        Map<UUID, OrderItem> orderItems = lockRequestedOrderItems(orderId, request);
        creationEligibilityResolver.requireEligible(creationEligibilityResolver.resolve(
                order, request, orderItems, returnedQuantities(orderItems, request)));

        OrderReturn orderReturn = OrderReturn.builder()
                .returnCode(generateUniqueReturnCode())
                .order(order)
                .status(ReturnStatus.CREATED)
                .refundRequired(request.getRefundRequired() == null ? Boolean.TRUE : request.getRefundRequired())
                .note(normalizeBlank(request.getNote()))
                .build();

        for (CreateOrderReturnItemRequest itemRequest : request.getItems()) {
            OrderItem orderItem = orderItems.get(itemRequest.getOrderItemId());
            if (orderItem != null && orderItem.isCustomBundle()) {
                throw new InvalidOrderReturnException("Trà đóng theo yêu cầu không áp dụng đổi trả");
            }

            orderReturn.addItem(OrderReturnItem.builder()
                    .orderItem(orderItem)
                    .quantity(itemRequest.getQuantity())
                    .reason(itemRequest.getReason())
                    .condition(itemRequest.getCondition())
                    .note(normalizeBlank(itemRequest.getNote()))
                    .build());
        }

        OrderReturn saved = orderReturnRepository.save(orderReturn);
        UUID actorUserId = currentActorUserIdOrNull();
        orderReturnHistoryWriter.writeIfAbsent(
                saved,
                OrderReturnHistoryAction.CREATED,
                null,
                ReturnStatus.CREATED,
                actorUserId,
                saved.getNote(),
                null,
                null
        );
        orderOutboxPublisher.publishReturnCreatedNotification(saved, actorUserId);

        return orderReturnMapper.toResponse(saved);
    }

    private Map<UUID, OrderItem> lockRequestedOrderItems(UUID orderId, CreateOrderReturnRequest request) {
        List<UUID> requestedOrderItemIds = request.getItems().stream()
                .map(CreateOrderReturnItemRequest::getOrderItemId)
                .toList();
        Map<UUID, OrderItem> lockedItems = orderItemRepository
                .findReturnableItemsForUpdate(orderId, requestedOrderItemIds)
                .stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        return lockedItems;
    }

    @Override
    @Transactional
    public OrderReturnResponse receiveReturn(UUID returnId) {
        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        if (ReturnStatus.CREATED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only created returns can be received");
        }

        ReturnStatus oldStatus = orderReturn.getStatus();
        orderReturn.setStatus(ReturnStatus.RECEIVED);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        UUID actorUserId = currentActorUserIdOrNull();
        orderReturnHistoryWriter.writeIfAbsent(
                saved,
                OrderReturnHistoryAction.RECEIVED,
                oldStatus,
                ReturnStatus.RECEIVED,
                actorUserId,
                "Store received returned goods",
                null,
                null
        );
        orderOutboxPublisher.publishReturnReceivedNotification(saved, oldStatus, actorUserId);

        return orderReturnMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OrderReturnResponse cancelReturn(UUID returnId) {
        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        if (ReturnStatus.CREATED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only created returns can be cancelled");
        }

        ReturnStatus oldStatus = orderReturn.getStatus();
        orderReturn.setStatus(ReturnStatus.CANCELLED);
        OrderReturn saved = orderReturnRepository.save(orderReturn);
        UUID actorUserId = currentActorUserIdOrNull();
        orderReturnHistoryWriter.writeIfAbsent(
                saved,
                OrderReturnHistoryAction.CANCELLED,
                oldStatus,
                ReturnStatus.CANCELLED,
                actorUserId,
                "Return cancelled",
                null,
                null
        );
        orderOutboxPublisher.publishReturnCancelledNotification(saved, oldStatus, actorUserId);

        return orderReturnMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderRefundSummaryResponse> getRefunds(
            String keyword,
            OrderRefundStatus status,
            RefundProgressStatus progressStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        // Refund progress includes Inventory and Payment data. Enrich all candidates
        // before filtering and slicing so page totals reflect the complete result set.
        List<OrderReturnSummaryResponse> summaries = orderReturnRepository.findRefundReturnSummaries(
                keywordPattern(keyword),
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        );
        List<OrderRefundSummaryResponse> refundRows = buildRefundSummaries(summaries);
        List<OrderRefundSummaryResponse> filteredRows = refundRows.stream()
                .filter(row -> status == null || row.getRefundStatus() == status)
                .filter(row -> progressStatus == null || row.getProgressStatus() == progressStatus)
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
        DownstreamLookupResult<InventoryClient.CustomerReturnQcSummaryResponse> qcLookup =
                lookupQcSummaryForRefundRead(returnId);
        DownstreamLookupResult<PaymentClient.PaymentRefundSummaryResponse> paymentLookup =
                lookupPaymentRefundSummaryForRefundRead(orderReturn);
        InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcLookup.value();
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = paymentLookup.value();
        OrderReturnPhysicalProgress physicalProgress = physicalProgressResolver.resolve(orderReturn);
        boolean physicalDone = physicalProgress.physicalDone();
        Order order = orderRepository.findByIdAndIsDeletedFalse(orderReturn.getOrder().getId()).orElse(null);
        BigDecimal refundAmount = null;
        if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
            refundAmount = paymentSummary.refundAmount();
        } else if (Boolean.TRUE.equals(orderReturn.getRefundRequired())) {
            if (order != null) {
                List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(order.getId());
                try {
                    refundAmount = returnRefundCalculator.calculate(order, orderReturn, orderReturns).refundAmount();
                } catch (Exception ex) {
                    log.warn("Could not calculate refund amount for returnId={}: {}", returnId, ex.getMessage());
                }
            }
        }

        RefundEligibilityResult eligibility = refundEligibilityResolver.resolve(
                orderReturn,
                physicalProgress,
                paymentSummary,
                paymentLookup.available(),
                refundAmount,
                order == null ? null : order.getPaidAmount()
        );

        boolean refundCompleted = orderReturn.getStatus() == ReturnStatus.COMPLETED
                || (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus()));
        String displayStatus = displayStatusResolver.resolveDisplayStatus(
                orderReturn.getStatus(),
                physicalProgress,
                orderReturn.getRefundRequired(),
                refundCompleted
        );

        String qcStatus = qcLookup.available()
                ? (qcSummary == null ? null : qcSummary.qcStatus())
                : DOWNSTREAM_STATUS_UNAVAILABLE;
        String paymentStatus = paymentLookup.available()
                ? (paymentSummary == null ? null : paymentSummary.paymentStatus())
                : DOWNSTREAM_STATUS_UNAVAILABLE;
        String method = paymentLookup.available()
                ? (paymentSummary == null ? null : paymentSummary.method())
                : DOWNSTREAM_METHOD_UNKNOWN;

        return OrderRefundDetailResponse.builder()
                .returnId(orderReturn.getId())
                .returnCode(orderReturn.getReturnCode())
                .orderId(orderReturn.getOrder().getId())
                .orderCode(orderReturn.getOrder().getOrderCode())
                .customerName(orderReturn.getOrder().getCustomerNameSnapshot())
                .customerPhone(orderReturn.getOrder().getCustomerPhoneSnapshot())
                .returnCreatedAt(orderReturn.getCreatedAt())
                .returnStatus(orderReturn.getStatus())
                .displayStatus(displayStatus)
                .refundRequired(orderReturn.getRefundRequired())
                .qcStatus(qcStatus)
                .physicalDone(physicalDone)
                .inventoryProcessingStatus(physicalProgress.inventoryProcessingStatus())
                .inspectionId(qcSummary == null ? null : qcSummary.inspectionId())
                .inventoryProcessedAt(qcSummary == null ? null : qcSummary.inspectedAt())
                .refundStatus(eligibility.refundStatus())
                .refundEligible(eligibility.refundEligible())
                .refundBlockReason(eligibility.blockReason())
                .refundAmount(refundAmount)
                .totalAmountRefunded(paymentLookup.available() && paymentSummary != null
                        ? paymentSummary.totalAmountRefunded()
                        : null)
                .paymentStatus(paymentStatus)
                .method(method)
                .refundedAt(paymentLookup.available() && paymentSummary != null ? paymentSummary.refundedAt() : null)
                .items(orderReturnMapper.toResponse(orderReturn).getItems())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnRefundPreviewResponse getRefundPreview(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
        OrderReturnPhysicalProgress physicalProgress = physicalProgressResolver.resolve(orderReturn);
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = lookupPaymentRefundSummary(orderReturn);

        Order order = orderRepository.findAllWithItemsByIdIn(List.of(orderReturn.getOrder().getId()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));
        List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(
                order.getId()
        );
        BigDecimal refundAmount = null;
        try {
            refundAmount = returnRefundCalculator
                    .calculate(order, orderReturn, orderReturns)
                    .refundAmount();
        } catch (Exception ex) {
            log.warn("Could not calculate refund amount for returnId={}: {}", returnId, ex.getMessage());
        }

        RefundEligibilityResult eligibility = refundEligibilityResolver.resolve(
                orderReturn,
                physicalProgress,
                paymentSummary,
                true,
                refundAmount,
                order.getPaidAmount()
        );

        return OrderReturnRefundPreviewResponse.builder()
                .returnId(returnId)
                .orderId(order.getId())
                .refundAmount(refundAmount)
                .paymentMethod(paymentSummary == null ? null : paymentSummary.method())
                .eligible(eligibility.refundEligible())
                .blockReason(eligibility.blockReason())
                .reason(eligibility.message())
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
                .pendingRefund(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.WAITING_REFUND).count())
                .refunded(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.REFUNDED).count())
                .notReady(rows.stream().filter(row -> row.getRefundStatus() == OrderRefundStatus.NOT_READY).count())
                .failed(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.FAILED).count())
                .waitingReceipt(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.WAITING_RECEIPT).count())
                .waitingInspection(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.WAITING_INSPECTION).count())
                .other(rows.stream().filter(row -> row.getProgressStatus() == RefundProgressStatus.OTHER).count())
                .build();
    }

    @Override
    public OrderReturnRefundResponse refundReturn(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));

        if (Boolean.FALSE.equals(orderReturn.getRefundRequired())) {
            throw new InvalidOrderReturnException("Refund is not required for this return");
        }

        PaymentClient.PaymentRefundSummaryResponse existingRefund = lookupPaymentRefundSummary(orderReturn);
        if (existingRefund != null && ("REFUNDED".equals(existingRefund.refundStatus())
                || (orderReturn.getStatus() == ReturnStatus.RECEIVED
                && existingRefund.refundTransactionId() != null
                && "PARTIALLY_REFUNDED".equals(existingRefund.refundStatus())))) {
            return existingCompletedRefundResponse(orderReturn, existingRefund);
        }

        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Return inventory inspection is not completed");
        }

        if (!isPhysicalDispositionCompleted(returnId)) {
            List<OrderReturnInventoryCommand> pendingCommands =
                    orderReturnInventoryCommandRepository.findByOrderReturnIdWithLines(returnId).stream()
                            .filter(c -> c.getStatus() == OrderReturnInventoryCommandStatus.PENDING)
                            .toList();
            for (OrderReturnInventoryCommand pending : pendingCommands) {
                try {
                    ReturnDispositionStatusResponse invStatus =
                            inventoryClient.getReturnDispositionStatus(pending.getCommandId());
                    if (invStatus != null) {
                        inventoryCommandReconciliationWriter.reconcileCommand(
                                OrderReturnInventoryCommandReconciliationWriter.CommandEvidence.from(invStatus)
                        );
                    }
                } catch (Exception ex) {
                    log.warn("Failed to reconcile command {} during refund execution: {}",
                            pending.getCommandId(), ex.getMessage());
                }
            }
            if (!isPhysicalDispositionCompleted(returnId)) {
                throw new InvalidOrderReturnException("Return inventory inspection is not completed");
            }
        }

        Order order = orderRepository.findByIdAndIsDeletedFalse(orderReturn.getOrder().getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderReturn.getOrder().getId()));

        List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(
                order.getId()
        );

        BigDecimal refundAmount = returnRefundCalculator
                .calculate(order, orderReturn, orderReturns)
                .refundAmount();

        OrderReturnPhysicalProgress physicalProgress = physicalProgressResolver.resolve(orderReturn);
        RefundEligibilityResult eligibility = refundEligibilityResolver.resolve(
                orderReturn,
                physicalProgress,
                existingRefund,
                true,
                refundAmount,
                order.getPaidAmount()
        );

        if (!eligibility.refundEligible()) {
            throw new InvalidOrderReturnException(
                    eligibility.message() != null ? eligibility.message() : "Return is not eligible for refund"
            );
        }

        // Feign call over network - NO DB locks held!
        PaymentClient.PaymentRefundResponse paymentRefund = paymentClient.refundOrderReturn(
                order.getId(),
                new PaymentClient.PaymentRefundRequest(returnId, refundAmount)
        );

        UUID actorUserId = currentActorUserIdOrNull();
        return orderReturnRefundTransactionService.applyRefundSuccess(returnId, paymentRefund, actorUserId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderReturnInventoryDispositionResponse submitInventoryDisposition(
            UUID returnId,
            SubmitReturnInventoryDispositionRequest request
    ) {
        validateDispositionRequest(request);

        OrderReturnInventoryCommand existingCommand = orderReturnInventoryCommandRepository
                .findByCommandIdAndIsDeletedFalse(request.getCommandId())
                .orElse(null);
        if (existingCommand != null) {
            return resolveExistingInventoryDispositionCommand(returnId, request, existingCommand);
        }

        OrderReturn orderReturn = getExistingReturnForUpdate(returnId);
        existingCommand = orderReturnInventoryCommandRepository
                .findByCommandIdAndIsDeletedFalse(request.getCommandId())
                .orElse(null);
        if (existingCommand != null) {
            return resolveExistingInventoryDispositionCommand(returnId, request, existingCommand);
        }

        // A new commandId is a new action, even when all business fields match an earlier action.
        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only received returns can request inventory disposition");
        }
        // A FAILED command can still have an outbox event or an unacknowledged
        // inspection. Do not release its quantity by choosing a new commandId.
        if (orderReturnInventoryCommandRepository.existsByOrderReturnIdAndStatusAndIsDeletedFalse(
                returnId, OrderReturnInventoryCommandStatus.FAILED)) {
            throw new InvalidOrderReturnException(
                    "Failed inventory disposition requires reconciliation before a new command");
        }

        Map<UUID, OrderReturnItem> returnItemsById = activeReturnItemsById(orderReturn);
        Map<UUID, SubmitReturnInventoryDispositionLineRequest> requestedLinesByReturnItemId =
                requestedDispositionLines(request);
        UUID actorUserId = currentActorUserIdOrNull();
        OrderReturnInventoryCommand command = OrderReturnInventoryCommand.builder()
                .orderReturn(orderReturn)
                .commandId(request.getCommandId())
                .status(OrderReturnInventoryCommandStatus.PENDING)
                .requestedBy(actorUserId)
                .build();

        for (SubmitReturnInventoryDispositionLineRequest lineRequest : requestedLinesByReturnItemId.values()) {
            OrderReturnItem returnItem = returnItemsById.get(lineRequest.getReturnItemId());
            if (returnItem == null) {
                throw new InvalidOrderReturnException("Return item does not belong to return");
            }

            validateDispositionLine(lineRequest, returnItem);
            OrderItem orderItem = returnItem.getOrderItem();
            if (orderItem == null || orderItem.getId() == null || orderItem.getProductVariantId() == null) {
                throw new InvalidOrderReturnException("Return item is missing order item data");
            }

            command.addLine(OrderReturnInventoryCommandLine.builder()
                    .returnItem(returnItem)
                    .orderItemId(orderItem.getId())
                    .productVariantId(orderItem.getProductVariantId())
                    .quantity(lineRequest.getQuantity())
                    .result(lineRequest.getResult())
                    .destinationLocationId(lineRequest.getDestinationLocationId())
                    .note(normalizeBlank(lineRequest.getNote()))
                    .build());
        }

        OrderReturnInventoryCommand saved = orderReturnInventoryCommandRepository.saveAndFlush(command);
        orderOutboxPublisher.publishReturnInventoryDispositionRequested(saved, actorUserId);

        return toDispositionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnInventoryDispositionResponse getInventoryDisposition(UUID returnId, UUID commandId) {
        OrderReturnInventoryCommand command = orderReturnInventoryCommandRepository
                .findByCommandIdAndIsDeletedFalse(commandId)
                .orElseThrow(() -> new OrderNotFoundException("Inventory disposition command not found: " + commandId));
        if (command.getOrderReturn() == null
                || command.getOrderReturn().getId() == null
                || !command.getOrderReturn().getId().equals(returnId)) {
            throw new OrderNotFoundException("Inventory disposition command does not belong to return: " + returnId);
        }
        return toDispositionResponse(command);
    }

    private OrderReturnInventoryDispositionResponse resolveExistingInventoryDispositionCommand(
            UUID returnId,
            SubmitReturnInventoryDispositionRequest request,
            OrderReturnInventoryCommand existingCommand
    ) {
        if (existingCommand.getOrderReturn() == null
                || existingCommand.getOrderReturn().getId() == null
                || !existingCommand.getOrderReturn().getId().equals(returnId)) {
            throw new InvalidOrderReturnException("Inventory disposition commandId belongs to another return");
        }
        if (!sameDispositionCommand(existingCommand, request)) {
            log.warn("Same commandId reused with different payload. commandId={}, returnId={}",
                    request.getCommandId(), returnId);
            throw new InvalidOrderReturnException("Inventory disposition commandId was already used differently");
        }

        return toDispositionResponse(existingCommand);
    }

    private void validateDispositionRequest(SubmitReturnInventoryDispositionRequest request) {
        if (request == null || request.getCommandId() == null) {
            throw new InvalidOrderReturnException("Inventory disposition commandId is required");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new InvalidOrderReturnException("Inventory disposition lines are required");
        }
        for (SubmitReturnInventoryDispositionLineRequest line : request.getLines()) {
            if (line != null && line.getResult() != null && !line.getResult().isAllowedForNewReturn()) {
                throw new InvalidOrderReturnException(
                        "QUARANTINE is no longer supported for new customer returns; use DAMAGED"
                );
            }
        }
    }

    private Map<UUID, SubmitReturnInventoryDispositionLineRequest> requestedDispositionLines(
            SubmitReturnInventoryDispositionRequest request
    ) {
        Map<UUID, SubmitReturnInventoryDispositionLineRequest> linesByReturnItemId =
                new LinkedHashMap<>();
        for (SubmitReturnInventoryDispositionLineRequest line : request.getLines()) {
            if (line == null || line.getReturnItemId() == null) {
                throw new InvalidOrderReturnException("Return item is required");
            }
            if (line.getResult() == null) {
                throw new InvalidOrderReturnException("Inventory disposition result is required");
            }
            if (linesByReturnItemId.put(line.getReturnItemId(), line) != null) {
                throw new InvalidOrderReturnException("Duplicate return item in inventory disposition request");
            }
        }

        return linesByReturnItemId;
    }

    private Map<UUID, OrderReturnItem> activeReturnItemsById(OrderReturn orderReturn) {
        if (orderReturn.getItems() == null) {
            return Map.of();
        }

        return orderReturn.getItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsDeleted()))
                .collect(Collectors.toMap(OrderReturnItem::getId, Function.identity()));
    }

    private void validateDispositionLine(
            SubmitReturnInventoryDispositionLineRequest lineRequest,
            OrderReturnItem returnItem
    ) {
        BigDecimal quantity = requirePositiveQuantity(lineRequest.getQuantity());
        if ((ReturnInventoryDispositionResult.RESTOCK == lineRequest.getResult()
                || ReturnInventoryDispositionResult.DAMAGED == lineRequest.getResult())
                && lineRequest.getDestinationLocationId() == null) {
            throw new InvalidOrderReturnException("Destination location is required for inventory disposition");
        }

        BigDecimal alreadyCommandedQuantity = defaultZero(
                orderReturnInventoryCommandRepository.sumCommandedQuantityByReturnItemId(returnItem.getId())
        );
        BigDecimal remainingQuantity = defaultZero(returnItem.getQuantity()).subtract(alreadyCommandedQuantity);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderReturnException("No remaining quantity available for inventory disposition");
        }
        if (quantity.compareTo(remainingQuantity) > 0) {
            throw new InvalidOrderReturnException("Inventory disposition quantity exceeds return item quantity");
        }
    }

    private boolean sameDispositionCommand(
            OrderReturnInventoryCommand command,
            SubmitReturnInventoryDispositionRequest request
    ) {
        Map<UUID, SubmitReturnInventoryDispositionLineRequest> requestedLines =
                requestedDispositionLines(request);
        List<OrderReturnInventoryCommandLine> commandLines = activeCommandLines(command);
        if (commandLines.size() != requestedLines.size()) {
            return false;
        }

        for (OrderReturnInventoryCommandLine commandLine : commandLines) {
            UUID returnItemId = commandLine.getReturnItem() == null ? null : commandLine.getReturnItem().getId();
            SubmitReturnInventoryDispositionLineRequest requestedLine = requestedLines.remove(returnItemId);
            if (requestedLine == null || !sameDispositionLine(commandLine, requestedLine)) {
                return false;
            }
        }

        return requestedLines.isEmpty();
    }

    private boolean sameDispositionLine(
            OrderReturnInventoryCommandLine commandLine,
            SubmitReturnInventoryDispositionLineRequest requestedLine
    ) {
        return defaultZero(commandLine.getQuantity()).compareTo(defaultZero(requestedLine.getQuantity())) == 0
                && commandLine.getResult() == requestedLine.getResult()
                && Objects.equals(commandLine.getDestinationLocationId(), requestedLine.getDestinationLocationId())
                && Objects.equals(normalizeBlank(commandLine.getNote()), normalizeBlank(requestedLine.getNote()));
    }

    private List<OrderReturnInventoryCommandLine> activeCommandLines(OrderReturnInventoryCommand command) {
        if (command.getLines() == null) {
            return List.of();
        }

        return command.getLines().stream()
                .filter(line -> Boolean.FALSE.equals(line.getIsDeleted()))
                .toList();
    }

    private OrderReturnInventoryDispositionResponse toDispositionResponse(
            OrderReturnInventoryCommand command
    ) {
        OrderReturn orderReturn = command.getOrderReturn();
        return OrderReturnInventoryDispositionResponse.builder()
                .commandId(command.getCommandId())
                .returnId(orderReturn == null ? null : orderReturn.getId())
                .returnCode(orderReturn == null ? null : orderReturn.getReturnCode())
                .status(command.getStatus())
                .executionAllowed(command.getStatus() == OrderReturnInventoryCommandStatus.PENDING
                        && orderReturn != null && orderReturn.getStatus() == ReturnStatus.RECEIVED
                        && !orderReturnInventoryCommandRepository.existsByOrderReturnIdAndStatusAndIsDeletedFalse(
                                orderReturn.getId(), OrderReturnInventoryCommandStatus.FAILED))
                .createdAt(command.getCreatedAt())
                .processedAt(command.getProcessedAt())
                .failedAt(command.getFailedAt())
                .failureCode(command.getFailureCode())
                .failureReason(command.getFailureReason())
                .inspectionId(command.getInspectionId())
                .lines(activeCommandLines(command).stream()
                        .map(this::toDispositionLineResponse)
                        .toList())
                .build();
    }

    private OrderReturnInventoryDispositionLineResponse toDispositionLineResponse(
            OrderReturnInventoryCommandLine line
    ) {
        OrderReturnItem returnItem = line.getReturnItem();
        return OrderReturnInventoryDispositionLineResponse.builder()
                .returnItemId(returnItem == null ? null : returnItem.getId())
                .orderItemId(line.getOrderItemId())
                .productVariantId(line.getProductVariantId())
                .quantity(line.getQuantity())
                .result(line.getResult())
                .destinationLocationId(line.getDestinationLocationId())
                .note(line.getNote())
                .build();
    }

    private OrderReturnRefundResponse existingCompletedRefundResponse(
            OrderReturn orderReturn,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        if (paymentSummary == null
                || (!"REFUNDED".equals(paymentSummary.refundStatus())
                && !"PARTIALLY_REFUNDED".equals(paymentSummary.refundStatus()))
                || paymentSummary.paymentId() == null || paymentSummary.refundTransactionId() == null
                || !orderReturn.getId().equals(paymentSummary.returnId())
                || !orderReturn.getOrder().getId().equals(paymentSummary.orderId())) {
            throw new InvalidOrderReturnException("Return already completed without a refund record");
        }
        if (orderReturn.getStatus() != ReturnStatus.COMPLETED) {
            if (orderReturn.getStatus() != ReturnStatus.RECEIVED || !isPhysicalDispositionCompleted(orderReturn.getId())) {
                throw new InvalidOrderReturnException("Return inventory inspection is not completed");
            }
        }

        // Payment lookup is complete before entering the short, proxied write transaction.
        // Reuse the same writer to reconcile a Payment success whose earlier Order write rolled back.
        return orderReturnRefundTransactionService.applyRefundSuccess(
                orderReturn.getId(),
                new PaymentClient.PaymentRefundResponse(
                        paymentSummary.paymentId(), paymentSummary.orderId(), paymentSummary.refundTransactionId(),
                        paymentSummary.returnId(), paymentSummary.refundAmount(), paymentSummary.totalAmountRefunded(),
                        paymentSummary.paymentStatus(), paymentSummary.method(), paymentSummary.refundedAt()
                ),
                currentActorUserIdOrNull()
        );
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
        return getReturns(keyword, status, null, fromDate, toDate, sort, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            String displayStatus,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size,
            boolean warehouseActiveOnly
    ) {
        String effectiveDisplayStatus = normalizeDisplayStatus(status, displayStatus);

        if (warehouseActiveOnly) {
            List<OrderReturnSummaryResponse> candidates = new ArrayList<>(orderReturnRepository.findReturnSummariesList(
                    keywordPattern(keyword),
                    null,
                    startOfDay(fromDate),
                    startOfDay(nextDay(toDate))
            ));
            enrichReturnSummaries(candidates);

            List<OrderReturnSummaryResponse> matched = candidates.stream()
                    .filter(row -> !"PENDING_REFUND".equalsIgnoreCase(row.getDisplayStatus()))
                    .filter(row -> effectiveDisplayStatus == null || "ALL".equalsIgnoreCase(effectiveDisplayStatus)
                            || effectiveDisplayStatus.equalsIgnoreCase(row.getDisplayStatus()))
                    .sorted(RETURN_PRIORITY_COMPARATOR)
                    .toList();

            int pageIndex = Math.max(page, 0);
            int pageSize = Math.min(Math.max(size, 1), 200);
            int fromIndex = Math.min(pageIndex * pageSize, matched.size());
            int toIndex = Math.min(fromIndex + pageSize, matched.size());
            return new PageImpl<>(
                    matched.subList(fromIndex, toIndex),
                    PageRequest.of(pageIndex, pageSize),
                    matched.size()
            );
        }

        if (effectiveDisplayStatus == null || "ALL".equalsIgnoreCase(effectiveDisplayStatus)) {
            // TODO: Global in-memory priority sorting for current scale.
            // When the dataset grows significantly, transition to a dedicated read-model
            // or persisted displayStatus with database indexing.
            List<OrderReturnSummaryResponse> candidates = new ArrayList<>(orderReturnRepository.findReturnSummariesList(
                    keywordPattern(keyword),
                    null,
                    startOfDay(fromDate),
                    startOfDay(nextDay(toDate))
            ));
            enrichReturnSummaries(candidates);
            candidates.sort(RETURN_PRIORITY_COMPARATOR);

            int pageIndex = Math.max(page, 0);
            int pageSize = Math.min(Math.max(size, 1), 200);
            int fromIndex = Math.min(pageIndex * pageSize, candidates.size());
            int toIndex = Math.min(fromIndex + pageSize, candidates.size());
            return new PageImpl<>(
                    candidates.subList(fromIndex, toIndex),
                    PageRequest.of(pageIndex, pageSize),
                    candidates.size()
            );
        }

        if ("CREATED".equalsIgnoreCase(effectiveDisplayStatus)) {
            Page<OrderReturnSummaryResponse> pageResult = orderReturnRepository.findReturnSummaries(
                    keywordPattern(keyword),
                    ReturnStatus.CREATED,
                    startOfDay(fromDate),
                    startOfDay(nextDay(toDate)),
                    returnPageRequest(page, size, sort)
            );
            enrichReturnSummaries(pageResult.getContent());
            return pageResult;
        }

        if ("CANCELLED".equalsIgnoreCase(effectiveDisplayStatus)) {
            Page<OrderReturnSummaryResponse> pageResult = orderReturnRepository.findReturnSummaries(
                    keywordPattern(keyword),
                    ReturnStatus.CANCELLED,
                    startOfDay(fromDate),
                    startOfDay(nextDay(toDate)),
                    returnPageRequest(page, size, sort)
            );
            enrichReturnSummaries(pageResult.getContent());
            return pageResult;
        }

        List<OrderReturnSummaryResponse> candidates = new ArrayList<>(orderReturnRepository.findReturnSummariesList(
                keywordPattern(keyword),
                null,
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        ));
        enrichReturnSummaries(candidates);

        List<OrderReturnSummaryResponse> matched = candidates.stream()
                .filter(row -> effectiveDisplayStatus.equalsIgnoreCase(row.getDisplayStatus()))
                .sorted(RETURN_PRIORITY_COMPARATOR)
                .toList();

        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 200);
        int fromIndex = Math.min(pageIndex * pageSize, matched.size());
        int toIndex = Math.min(fromIndex + pageSize, matched.size());
        return new PageImpl<>(
                matched.subList(fromIndex, toIndex),
                PageRequest.of(pageIndex, pageSize),
                matched.size()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderReturnSummaryResponse> getFilteredReturns(
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
    ) {
        String effectiveDisplayStatus = normalizeDisplayStatus(status, displayStatus);

        if (warehouseActiveOnly) {
            List<OrderReturnSummaryResponse> candidates = orderReturnRepository.findFilteredReturnSummariesList(
                    keywordPattern(keyword), keywordPattern(returnCode), keywordPattern(orderCode),
                    startOfDay(fromDate), startOfDay(nextDay(toDate))
            );
            enrichReturnSummaries(candidates);

            List<OrderReturnSummaryResponse> matched = candidates.stream()
                    .filter(row -> !"PENDING_REFUND".equalsIgnoreCase(row.getDisplayStatus()))
                    .filter(row -> effectiveDisplayStatus == null || "ALL".equalsIgnoreCase(effectiveDisplayStatus)
                            || effectiveDisplayStatus.equalsIgnoreCase(row.getDisplayStatus()))
                    .sorted(RETURN_PRIORITY_COMPARATOR)
                    .toList();

            int pageIndex = Math.max(page, 0);
            int pageSize = Math.min(Math.max(size, 1), 200);
            int fromIndex = Math.min(pageIndex * pageSize, matched.size());
            int toIndex = Math.min(fromIndex + pageSize, matched.size());
            return new PageImpl<>(
                    matched.subList(fromIndex, toIndex),
                    PageRequest.of(pageIndex, pageSize),
                    matched.size()
            );
        }

        if (displayStatus != null && !"ALL".equalsIgnoreCase(displayStatus)
                && status == null) {
            List<OrderReturnSummaryResponse> candidates = orderReturnRepository.findFilteredReturnSummariesList(
                    keywordPattern(keyword), keywordPattern(returnCode), keywordPattern(orderCode),
                    startOfDay(fromDate), startOfDay(nextDay(toDate))
            );
            enrichReturnSummaries(candidates);
            List<OrderReturnSummaryResponse> matched = candidates.stream()
                    .filter(row -> displayStatus.equalsIgnoreCase(row.getDisplayStatus()))
                    .sorted(RETURN_PRIORITY_COMPARATOR)
                    .toList();
            int pageIndex = Math.max(page, 0);
            int pageSize = Math.min(Math.max(size, 1), 200);
            int fromIndex = Math.min(pageIndex * pageSize, matched.size());
            return new PageImpl<>(
                    matched.subList(fromIndex, Math.min(fromIndex + pageSize, matched.size())),
                    PageRequest.of(pageIndex, pageSize), matched.size()
            );
        }
        Page<OrderReturnSummaryResponse> result = orderReturnRepository.findFilteredReturnSummaries(
                keywordPattern(keyword),
                keywordPattern(returnCode),
                keywordPattern(orderCode),
                status,
                startOfDay(fromDate),
                startOfDay(nextDay(toDate)),
                returnPageRequest(page, size, sort)
        );
        enrichReturnSummaries(result.getContent());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getReturnStatusCounts(String keyword, LocalDate fromDate, LocalDate toDate, boolean warehouseActiveOnly) {
        List<OrderReturnSummaryResponse> summaries = orderReturnRepository.findReturnSummariesList(
                keywordPattern(keyword),
                null,
                startOfDay(fromDate),
                startOfDay(nextDay(toDate))
        );
        enrichReturnSummaries(summaries);

        long createdCount = 0;
        long receivedCount = 0;
        long processingCount = 0;
        long processingFailedCount = 0;
        long pendingRefundCount = 0;
        long completedCount = 0;
        long cancelledCount = 0;

        for (OrderReturnSummaryResponse row : summaries) {
            String ds = row.getDisplayStatus();
            if ("CREATED".equals(ds)) {
                createdCount++;
            } else if ("RECEIVED".equals(ds)) {
                receivedCount++;
            } else if ("PROCESSING".equals(ds)) {
                processingCount++;
            } else if ("PROCESSING_FAILED".equals(ds)) {
                processingFailedCount++;
            } else if ("PENDING_REFUND".equals(ds)) {
                pendingRefundCount++;
            } else if ("COMPLETED".equals(ds)) {
                completedCount++;
            } else if ("CANCELLED".equals(ds)) {
                cancelledCount++;
            }
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        if (warehouseActiveOnly) {
            long warehouseTotal = createdCount + receivedCount + processingCount + processingFailedCount + completedCount + cancelledCount;
            counts.put("ALL", warehouseTotal);
            counts.put("CREATED", createdCount);
            counts.put("RECEIVED", receivedCount);
            counts.put("PROCESSING", processingCount);
            counts.put("COMPLETED", completedCount);
            counts.put("CANCELLED", cancelledCount);
            counts.put("PROCESSING_FAILED", processingFailedCount);
        } else {
            counts.put("ALL", (long) summaries.size());
            counts.put("CREATED", createdCount);
            counts.put("RECEIVED", receivedCount);
            counts.put("PROCESSING", processingCount);
            counts.put("PENDING_REFUND", pendingRefundCount);
            counts.put("COMPLETED", completedCount);
            counts.put("CANCELLED", cancelledCount);
            counts.put("PROCESSING_FAILED", processingFailedCount);
        }

        return counts;
    }

    private String normalizeDisplayStatus(ReturnStatus status, String displayStatus) {
        if (displayStatus != null && !displayStatus.isBlank()) {
            return displayStatus.trim().toUpperCase(Locale.ROOT);
        }
        if (status != null) {
            return status.name();
        }
        return null;
    }

    private void enrichReturnSummaries(List<OrderReturnSummaryResponse> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }

        for (OrderReturnSummaryResponse summary : summaries) {
            if (summary.getStatus() == ReturnStatus.CREATED) {
                summary.setQcStatus("PENDING");
                summary.setRefundStatus(Boolean.FALSE.equals(summary.getRefundRequired())
                        ? OrderRefundStatus.NOT_REQUIRED
                        : OrderRefundStatus.NOT_READY);
                summary.setHasFailedDisposition(false);
                summary.setIsPendingDisposition(false);
                summary.setDisplayStatus("CREATED");
                summary.setPhysicalDone(false);
                summary.setRequiredQuantity(BigDecimal.ZERO);
                summary.setProcessedQuantity(BigDecimal.ZERO);
                summary.setRemainingQuantity(BigDecimal.ZERO);
                summary.setInventoryProcessingStatus("PENDING");
            } else if (summary.getStatus() == ReturnStatus.CANCELLED) {
                summary.setHasFailedDisposition(false);
                summary.setIsPendingDisposition(false);
                summary.setDisplayStatus("CANCELLED");
                summary.setPhysicalDone(false);
                summary.setRequiredQuantity(BigDecimal.ZERO);
                summary.setProcessedQuantity(BigDecimal.ZERO);
                summary.setRemainingQuantity(BigDecimal.ZERO);
                summary.setInventoryProcessingStatus("PENDING");
            } else if (summary.getStatus() == ReturnStatus.COMPLETED) {
                summary.setHasFailedDisposition(false);
                summary.setIsPendingDisposition(false);
                summary.setDisplayStatus("COMPLETED");
                summary.setPhysicalDone(true);
                summary.setInventoryProcessingStatus("COMPLETED");
                summary.setRefundStatus(Boolean.FALSE.equals(summary.getRefundRequired())
                        ? OrderRefundStatus.NOT_REQUIRED
                        : OrderRefundStatus.REFUNDED);
            }
        }

        List<OrderReturnSummaryResponse> activeSummaries = summaries.stream()
                .filter(s -> s.getStatus() == ReturnStatus.RECEIVED || s.getStatus() == ReturnStatus.COMPLETED)
                .toList();

        if (activeSummaries.isEmpty()) {
            return;
        }

        List<UUID> activeReturnIds = activeSummaries.stream()
                .map(OrderReturnSummaryResponse::getId)
                .toList();

        Set<UUID> completedRefundReturnIds = orderReturnHistoryRepository.findCompletedRefundReturnIds(activeReturnIds);

        Map<UUID, OrderReturn> returnsById = orderReturnRepository
                .findByIdInAndIsDeletedFalseWithItems(activeReturnIds)
                .stream()
                .collect(Collectors.toMap(OrderReturn::getId, Function.identity()));

        Map<UUID, OrderReturnPhysicalProgress> physicalProgressMap =
                physicalProgressResolver.resolveBatch(returnsById.values());

        List<OrderReturnSummaryResponse> receivedSummaries = activeSummaries.stream()
                .filter(s -> s.getStatus() == ReturnStatus.RECEIVED)
                .toList();

        DownstreamLookupResult<Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse>> qcLookup =
                lookupQcSummariesForRefundRead(receivedSummaries, returnsById);
        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> qcByReturnId = qcLookup.value();

        for (OrderReturnSummaryResponse summary : activeSummaries) {
            UUID returnId = summary.getId();

            OrderReturnPhysicalProgress progress = physicalProgressMap.getOrDefault(
                    returnId,
                    OrderReturnPhysicalProgress.empty()
            );

            summary.setPhysicalDone(progress.physicalDone());
            summary.setRequiredQuantity(progress.requiredQuantity());
            summary.setProcessedQuantity(progress.processedQuantity());
            summary.setRemainingQuantity(progress.remainingQuantity());
            summary.setInventoryProcessingStatus(progress.inventoryProcessingStatus());

            if (summary.getStatus() == ReturnStatus.COMPLETED) {
                summary.setPhysicalDone(true);
                summary.setDisplayStatus("COMPLETED");
                continue;
            }

            InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcByReturnId.get(returnId);
            BigDecimal remainingQcQuantity = null;
            BigDecimal postedQcQuantity = null;
            String qcStatus = null;
            String inspectionStatus = null;

            if (qcSummary != null) {
                qcStatus = qcSummary.qcStatus();
                inspectionStatus = qcSummary.inspectionStatus();
                if (qcSummary.lines() != null) {
                    remainingQcQuantity = qcSummary.lines().stream()
                            .map(InventoryClient.CustomerReturnQcLineResponse::remainingQcQuantity)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    postedQcQuantity = qcSummary.lines().stream()
                            .map(InventoryClient.CustomerReturnQcLineResponse::postedQcQuantity)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                }
            }
            summary.setQcStatus(qcStatus);
            summary.setInspectionStatus(inspectionStatus);
            summary.setRemainingQcQuantity(remainingQcQuantity);
            summary.setPostedQcQuantity(postedQcQuantity);

            boolean physicalDone = progress.physicalDone();
            boolean hasActivePending = !physicalDone && progress.hasPendingCommand();
            boolean hasActiveFailure = !physicalDone && progress.hasFailedCommand();

            summary.setHasFailedDisposition(hasActiveFailure);
            summary.setIsPendingDisposition(hasActivePending);

            boolean refundCompleted = completedRefundReturnIds.contains(returnId);
            OrderRefundStatus refundStatus;
            if (Boolean.FALSE.equals(summary.getRefundRequired())) {
                refundStatus = OrderRefundStatus.NOT_REQUIRED;
            } else if (refundCompleted) {
                refundStatus = OrderRefundStatus.REFUNDED;
            } else if (physicalDone) {
                refundStatus = OrderRefundStatus.PENDING;
            } else {
                refundStatus = OrderRefundStatus.NOT_READY;
            }
            summary.setRefundStatus(refundStatus);

            String derivedDisplayStatus = displayStatusResolver.resolveDisplayStatus(
                    summary.getStatus(),
                    progress,
                    summary.getRefundRequired(),
                    refundCompleted
            );
            summary.setDisplayStatus(derivedDisplayStatus);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderReturnResponse> getReturnsByOrder(UUID orderId) {
        if (!orderRepository.findByIdAndIsDeletedFalse(orderId).isPresent()) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        List<OrderReturn> returns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(orderId);
        if (returns.isEmpty()) {
            return List.of();
        }

        List<UUID> returnIds = returns.stream().map(OrderReturn::getId).toList();
        Set<UUID> completedRefundReturnIds = orderReturnHistoryRepository.findCompletedRefundReturnIds(returnIds);
        Map<UUID, OrderReturnPhysicalProgress> progressMap = physicalProgressResolver.resolveBatch(returns);

        return returns.stream()
                .map(r -> {
                    OrderReturnPhysicalProgress progress = progressMap.getOrDefault(r.getId(), OrderReturnPhysicalProgress.empty());
                    boolean refundCompleted = completedRefundReturnIds.contains(r.getId());
                    String displayStatus = displayStatusResolver.resolveDisplayStatus(
                            r.getStatus(),
                            progress,
                            r.getRefundRequired(),
                            refundCompleted
                    );
                    return orderReturnMapper.toResponse(r, progress, displayStatus);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderReturnResponse getReturnById(UUID returnId) {
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
        OrderReturnPhysicalProgress progress = physicalProgressResolver.resolve(orderReturn);
        boolean refundCompleted = orderReturnHistoryRepository.existsByOrderReturnIdAndActionAndIsDeletedFalse(
                returnId,
                OrderReturnHistoryAction.REFUND_COMPLETED
        );
        String displayStatus = displayStatusResolver.resolveDisplayStatus(
                orderReturn.getStatus(),
                progress,
                orderReturn.getRefundRequired(),
                refundCompleted
        );
        return orderReturnMapper.toResponse(orderReturn, progress, displayStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderReturnTimelineResponse> getReturnTimeline(UUID returnId) {
        if (!orderReturnRepository.findByIdAndIsDeletedFalse(returnId).isPresent()) {
            throw new OrderNotFoundException("Return not found: " + returnId);
        }

        return orderReturnHistoryRepository
                .findByOrderReturnIdAndIsDeletedFalseOrderByOccurredAtAscIdAsc(returnId)
                .stream()
                .map(this::toTimelineResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPhysicalDispositionCompleted(UUID returnId) {
        return physicalCompletionHandler.isPhysicalDispositionCompleted(returnId);
    }

    @Override
    @Transactional
    public void checkAndAutoCompleteNoRefundReturn(UUID returnId) {
        physicalCompletionHandler.checkAndAutoCompleteNoRefundReturn(returnId);
    }

    @Override
    @Transactional
    public void recordPhysicalCompletion(UUID returnId, UUID actorUserId, UUID inspectionId) {
        physicalCompletionHandler.recordPhysicalCompletion(returnId, actorUserId, inspectionId);
    }

    private OrderReturn getExistingReturnForUpdate(UUID returnId) {
        return orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
    }


    private OrderReturnTimelineResponse toTimelineResponse(OrderReturnHistory history) {
        return OrderReturnTimelineResponse.builder()
                .id(history.getId())
                .action(history.getAction())
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .actorUserId(history.getActorUserId())
                .note(history.getNote())
                .referenceType(history.getReferenceType())
                .referenceId(history.getReferenceId())
                .occurredAt(history.getOccurredAt())
                .build();
    }

    private UUID currentActorUserIdOrNull() {
        try {
            String userId = SecurityUtils.currentUserId();
            return userId == null || userId.isBlank() ? null : UUID.fromString(userId);
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            return null;
        }
    }

    private List<OrderRefundSummaryResponse> buildRefundSummaries(List<OrderReturnSummaryResponse> summaries) {
        int summaryCount = summaries == null ? 0 : summaries.size();
        log.info("Loaded {} refund return summaries from repository", summaryCount);
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

        List<UUID> orderIds = summaries.stream()
                .map(OrderReturnSummaryResponse::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Order> ordersById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllWithItemsByIdIn(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));
        Map<UUID, List<OrderReturn>> returnsByOrderId = orderIds.isEmpty()
                ? Map.of()
                : orderReturnRepository.findByOrderIdInAndIsDeletedFalseWithItems(orderIds).stream()
                        .collect(Collectors.groupingBy(r -> r.getOrder().getId()));

        DownstreamLookupResult<Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse>> qcLookup =
                lookupQcSummariesForRefundRead(summaries, returnsById);
        DownstreamLookupResult<Map<UUID, PaymentClient.PaymentRefundSummaryResponse>> paymentLookup =
                lookupPaymentRefundSummariesForRefundRead(summaries);
        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> qcByReturnId = qcLookup.value();
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> paymentByReturnId = paymentLookup.value();
        Map<UUID, OrderReturnPhysicalProgress> physicalProgressMap =
                physicalProgressResolver.resolveBatch(returnsById.values());

        return summaries.stream()
                .map(summary -> {
                    OrderReturn orderReturn = returnsById.get(summary.getId());
                    InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcByReturnId.get(summary.getId());
                    PaymentClient.PaymentRefundSummaryResponse paymentSummary = paymentByReturnId.get(summary.getId());
                    OrderReturnPhysicalProgress physicalProgress = physicalProgressMap.getOrDefault(
                            summary.getId(),
                            OrderReturnPhysicalProgress.empty()
                    );
                    boolean physicalDone = physicalProgress.physicalDone();

                    Order order = ordersById.get(summary.getOrderId());
                    BigDecimal refundAmount = null;
                    if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
                        refundAmount = paymentSummary.refundAmount();
                    } else if (orderReturn != null && Boolean.TRUE.equals(orderReturn.getRefundRequired())) {
                        List<OrderReturn> orderReturns = returnsByOrderId.getOrDefault(summary.getOrderId(), List.of());
                        if (order != null) {
                            try {
                                refundAmount = returnRefundCalculator.calculate(order, orderReturn, orderReturns).refundAmount();
                            } catch (Exception ex) {
                                log.warn("Could not calculate refund amount for returnId={}: {}", orderReturn.getId(), ex.getMessage());
                            }
                        }
                    }

                    RefundEligibilityResult eligibility = refundEligibilityResolver.resolve(
                            orderReturn,
                            physicalProgress,
                            paymentSummary,
                            paymentLookup.available(),
                            refundAmount,
                            order == null ? null : order.getPaidAmount()
                    );

                    boolean refundRequired = orderReturn == null
                            ? Boolean.TRUE.equals(summary.getRefundRequired())
                            : Boolean.TRUE.equals(orderReturn.getRefundRequired());
                    boolean refundCompleted = (orderReturn != null && orderReturn.getStatus() == ReturnStatus.COMPLETED)
                            || (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus()));
                    String displayStatus = displayStatusResolver.resolveDisplayStatus(
                            orderReturn != null ? orderReturn.getStatus() : summary.getStatus(),
                            physicalProgress,
                            refundRequired,
                            refundCompleted
                    );

                    String qcStatus = qcLookup.available()
                            ? (qcSummary == null ? null : qcSummary.qcStatus())
                            : DOWNSTREAM_STATUS_UNAVAILABLE;
                    String paymentStatus = paymentLookup.available()
                            ? (paymentSummary == null ? null : paymentSummary.paymentStatus())
                            : DOWNSTREAM_STATUS_UNAVAILABLE;
                    String method = paymentLookup.available()
                            ? (paymentSummary == null ? null : paymentSummary.method())
                            : DOWNSTREAM_METHOD_UNKNOWN;

                    OrderRefundSummaryResponse row = OrderRefundSummaryResponse.builder()
                            .returnId(summary.getId())
                            .returnCode(summary.getReturnCode())
                            .orderId(summary.getOrderId())
                            .orderCode(summary.getOrderCode())
                            .customerName(summary.getCustomerName())
                            .customerPhone(summary.getCustomerPhone())
                            .returnCreatedAt(summary.getCreatedAt())
                            .returnStatus(summary.getStatus())
                            .displayStatus(displayStatus)
                            .refundRequired(refundRequired)
                            .qcStatus(qcStatus)
                            .physicalDone(physicalDone)
                            .inventoryProcessingStatus(physicalProgress.inventoryProcessingStatus())
                            .inspectionId(qcSummary == null ? null : qcSummary.inspectionId())
                            .inventoryProcessedAt(qcSummary == null ? null : qcSummary.inspectedAt())
                            .refundStatus(eligibility.refundStatus())
                            .refundEligible(eligibility.refundEligible())
                            .refundBlockReason(eligibility.blockReason())
                            .refundAmount(refundAmount)
                            .paymentStatus(paymentStatus)
                            .method(method)
                            .refundedAt(paymentLookup.available() && paymentSummary != null
                                    ? paymentSummary.refundedAt()
                                    : null)
                            .build();
                    row.setProgressStatus(RefundProgressStatusResolver.resolve(row));
                    return row;
                })
                .toList();
    }

    private DownstreamLookupResult<Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse>> lookupQcSummariesForRefundRead(
            List<OrderReturnSummaryResponse> summaries,
            Map<UUID, OrderReturn> returnsById
    ) {
        List<InventoryClient.CustomerReturnSnapshot> snapshots = summaries.stream()
                .map(summary -> returnsById.get(summary.getId()))
                .filter(orderReturn -> orderReturn != null)
                .map(this::toCustomerReturnSnapshot)
                .toList();
        if (snapshots.isEmpty()) {
            return new DownstreamLookupResult<>(Map.of(), true);
        }

        log.info("Starting Inventory enrich for {} refund returns", snapshots.size());
        try {
            Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> summariesByReturnId =
                    inventoryClient.getCustomerReturnQcSummaries(
                                    new InventoryClient.CustomerReturnQcSummaryBatchRequest(snapshots)
                            )
                            .stream()
                            .collect(Collectors.toMap(
                                    InventoryClient.CustomerReturnQcSummaryResponse::returnId,
                                    Function.identity(),
                                    (first, second) -> first
                            ));
            log.info(
                    "Inventory enrich succeeded for {} refund returns with {} QC summaries",
                    snapshots.size(),
                    summariesByReturnId.size()
            );
            return new DownstreamLookupResult<>(summariesByReturnId, true);
        } catch (RetryableException ex) {
            logDownstreamUnavailable("Inventory-Service", "customer return QC batch summary", ex, ex.status());
            return new DownstreamLookupResult<>(Map.of(), false);
        } catch (FeignClientException ex) {
            if (isDownstreamReadUnavailableStatus(ex.getStatus())) {
                logDownstreamUnavailable(
                        "Inventory-Service",
                        "customer return QC batch summary",
                        ex,
                        ex.getStatus()
                );
                return new DownstreamLookupResult<>(Map.of(), false);
            }
            throw ex;
        } catch (FeignException ex) {
            if (isAvailabilityStatus(ex.status())) {
                logDownstreamUnavailable("Inventory-Service", "customer return QC batch summary", ex, ex.status());
                return new DownstreamLookupResult<>(Map.of(), false);
            }
            logDownstreamFailureWithoutDegradation("Inventory-Service", "customer return QC batch summary", ex);
            throw ex;
        }
    }

    private DownstreamLookupResult<InventoryClient.CustomerReturnQcSummaryResponse> lookupQcSummaryForRefundRead(
            UUID returnId
    ) {
        log.info("Starting Inventory enrich for refund return {}", returnId);
        try {
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                    inventoryClient.getCustomerReturnQcSummary(returnId);
            log.info("Inventory enrich succeeded for refund return {}", returnId);
            return new DownstreamLookupResult<>(qcSummary, true);
        } catch (RetryableException ex) {
            logDownstreamUnavailable("Inventory-Service", "customer return QC summary", ex, ex.status());
            return new DownstreamLookupResult<>(null, false);
        } catch (FeignClientException ex) {
            if (isDownstreamReadUnavailableStatus(ex.getStatus())) {
                logDownstreamUnavailable(
                        "Inventory-Service",
                        "customer return QC summary",
                        ex,
                        ex.getStatus()
                );
                return new DownstreamLookupResult<>(null, false);
            }
            throw ex;
        } catch (FeignException ex) {
            if (isAvailabilityStatus(ex.status())) {
                logDownstreamUnavailable("Inventory-Service", "customer return QC summary", ex, ex.status());
                return new DownstreamLookupResult<>(null, false);
            }
            logDownstreamFailureWithoutDegradation("Inventory-Service", "customer return QC summary", ex);
            throw ex;
        }
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

    private DownstreamLookupResult<Map<UUID, PaymentClient.PaymentRefundSummaryResponse>> lookupPaymentRefundSummariesForRefundRead(
            List<OrderReturnSummaryResponse> summaries
    ) {
        List<PaymentClient.RefundSummaryLookupItem> items = summaries.stream()
                .map(summary -> new PaymentClient.RefundSummaryLookupItem(summary.getId(), summary.getOrderId()))
                .toList();
        if (items.isEmpty()) {
            return new DownstreamLookupResult<>(Map.of(), true);
        }

        log.info("Starting Payment enrich for {} refund returns", items.size());
        try {
            Map<UUID, PaymentClient.PaymentRefundSummaryResponse> summariesByReturnId =
                    paymentClient.lookupRefundSummaries(new PaymentClient.RefundSummaryLookupRequest(items))
                            .stream()
                            .collect(Collectors.toMap(
                                    PaymentClient.PaymentRefundSummaryResponse::returnId,
                                    Function.identity(),
                                    (first, second) -> first
                            ));
            log.info(
                    "Payment enrich succeeded for {} refund returns with {} payment summaries",
                    items.size(),
                    summariesByReturnId.size()
            );
            return new DownstreamLookupResult<>(summariesByReturnId, true);
        } catch (RetryableException ex) {
            logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.status());
            return new DownstreamLookupResult<>(Map.of(), false);
        } catch (FeignClientException ex) {
            if (isDownstreamReadUnavailableStatus(ex.getStatus())) {
                logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.getStatus());
                return new DownstreamLookupResult<>(Map.of(), false);
            }
            throw ex;
        } catch (FeignException ex) {
            if (isAvailabilityStatus(ex.status())) {
                logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.status());
                return new DownstreamLookupResult<>(Map.of(), false);
            }
            logDownstreamFailureWithoutDegradation("Payment-Service", "refund summary lookup", ex);
            throw ex;
        }
    }

    private DownstreamLookupResult<PaymentClient.PaymentRefundSummaryResponse> lookupPaymentRefundSummaryForRefundRead(
            OrderReturn orderReturn
    ) {
        log.info("Starting Payment enrich for refund return {}", orderReturn.getId());
        try {
            PaymentClient.PaymentRefundSummaryResponse paymentSummary = lookupPaymentRefundSummary(orderReturn);
            log.info("Payment enrich succeeded for refund return {}", orderReturn.getId());
            return new DownstreamLookupResult<>(paymentSummary, true);
        } catch (RetryableException ex) {
            logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.status());
            return new DownstreamLookupResult<>(null, false);
        } catch (FeignClientException ex) {
            if (isDownstreamReadUnavailableStatus(ex.getStatus())) {
                logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.getStatus());
                return new DownstreamLookupResult<>(null, false);
            }
            throw ex;
        } catch (FeignException ex) {
            if (isAvailabilityStatus(ex.status())) {
                logDownstreamUnavailable("Payment-Service", "refund summary lookup", ex, ex.status());
                return new DownstreamLookupResult<>(null, false);
            }
            logDownstreamFailureWithoutDegradation("Payment-Service", "refund summary lookup", ex);
            throw ex;
        }
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


    private boolean isAvailabilityStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private boolean isDownstreamReadUnavailableStatus(int status) {
        return status == 401 || status == 403 || isAvailabilityStatus(status);
    }

    private void logDownstreamUnavailable(
            String serviceName,
            String operation,
            RuntimeException ex,
            Integer status
    ) {
        log.warn(
                "{} {} unavailable: status={}, exception={}, message={}",
                serviceName,
                operation,
                status == null ? "n/a" : status,
                ex.getClass().getSimpleName(),
                shortMessage(ex)
        );
    }

    private void logDownstreamFailureWithoutDegradation(
            String serviceName,
            String operation,
            FeignException ex
    ) {
        String failureType = ex.status() == 401 || ex.status() == 403
                ? "auth/security failure"
                : "failed without degradation";
        log.warn(
                "{} {} {}: status={}, exception={}, message={}",
                serviceName,
                operation,
                failureType,
                ex.status(),
                ex.getClass().getSimpleName(),
                shortMessage(ex)
        );
    }

    private String shortMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private record DownstreamLookupResult<T>(T value, boolean available) {}

    private OrderReturnStatus resolveOrderReturnStatus(Order order, List<OrderReturn> orderReturns) {
        Map<UUID, BigDecimal> completedReturnedQuantities = orderReturns.stream()
                .filter(returnRecord -> ReturnStatus.RECEIVED == returnRecord.getStatus()
                        || ReturnStatus.COMPLETED == returnRecord.getStatus())
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


    private static int getRefundPriority(OrderRefundSummaryResponse row) {
        if (row == null) {
            return 99;
        }
        OrderRefundStatus status = row.getRefundStatus();
        if (status == OrderRefundStatus.FAILED) {
            return 1;
        }
        if (status == OrderRefundStatus.PENDING) {
            return 2;
        }
        if (status == OrderRefundStatus.NOT_READY) {
            if (row.getReturnStatus() == ReturnStatus.RECEIVED) {
                return 3;
            }
            if (row.getReturnStatus() == ReturnStatus.CREATED) {
                return 4;
            }
            return 4;
        }
        if (status == OrderRefundStatus.REFUNDED) {
            return 5;
        }
        if (status == OrderRefundStatus.NOT_REQUIRED) {
            return 6;
        }
        return 7;
    }

    private Comparator<OrderRefundSummaryResponse> refundSort(String sort) {
        boolean ascending = sort != null && sort.toLowerCase(Locale.ROOT).contains("asc");
        Comparator<OrderRefundSummaryResponse> createdComparator = ascending
                ? Comparator.comparing(OrderRefundSummaryResponse::getReturnCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(OrderRefundSummaryResponse::getReturnCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        Comparator<OrderRefundSummaryResponse> idComparator = ascending
                ? Comparator.comparing(row -> row.getReturnId() == null ? "" : row.getReturnId().toString())
                : Comparator.comparing(row -> row.getReturnId() == null ? "" : row.getReturnId().toString(), Comparator.reverseOrder());

        return Comparator
                .comparingInt(OrderReturnServiceImpl::getRefundPriority)
                .thenComparing(createdComparator)
                .thenComparing(idComparator);
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
