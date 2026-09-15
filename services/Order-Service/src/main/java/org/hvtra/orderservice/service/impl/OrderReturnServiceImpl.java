package org.hvtra.orderservice.service.impl;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnHistory;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommand;
import org.hvtra.orderservice.domain.entity.OrderReturnInventoryCommandLine;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnInventoryCommandStatus;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.OrderReturnStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnInventoryDispositionResult;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
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
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderItemRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnHistoryRepository;
import org.hvtra.orderservice.repository.OrderReturnInventoryCommandRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.ReturnRefundCalculator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.hvtra.orderservice.service.support.OrderReturnHistoryWriter;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private static final List<ReturnStatus> ACTIVE_RETURN_STATUSES =
            List.of(ReturnStatus.CREATED, ReturnStatus.RECEIVED, ReturnStatus.COMPLETED);
    private static final Duration DEFAULT_CHANGE_OF_MIND_WINDOW = Duration.ofDays(7);
    private static final Duration DEFAULT_QUALITY_WINDOW = Duration.ofDays(14);
    private static final String DOWNSTREAM_STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String DOWNSTREAM_METHOD_UNKNOWN = "UNKNOWN";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final OrderReturnInventoryCommandRepository orderReturnInventoryCommandRepository;
    private final OrderReturnHistoryRepository orderReturnHistoryRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderReturnMapper orderReturnMapper;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final ReturnRefundCalculator returnRefundCalculator;
    private final OrderReturnHistoryWriter orderReturnHistoryWriter;
    private final OrderOutboxPublisher orderOutboxPublisher;

    @Override
    @Transactional
    public OrderReturnResponse createReturn(UUID orderId, CreateOrderReturnRequest request) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        validateOrderEligibility(order);
        validateRequest(request);

        Map<UUID, OrderItem> orderItems = lockRequestedOrderItems(orderId, request);

        OrderReturn orderReturn = OrderReturn.builder()
                .returnCode(generateUniqueReturnCode())
                .order(order)
                .status(ReturnStatus.CREATED)
                .refundRequired(request.getRefundRequired() == null ? Boolean.TRUE : request.getRefundRequired())
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

        for (CreateOrderReturnItemRequest itemRequest : request.getItems()) {
            if (!lockedItems.containsKey(itemRequest.getOrderItemId())) {
                throw new InvalidOrderReturnException("Order item does not belong to order");
            }
        }

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
        DownstreamLookupResult<InventoryClient.CustomerReturnQcSummaryResponse> qcLookup =
                lookupQcSummaryForRefundRead(returnId);
        DownstreamLookupResult<PaymentClient.PaymentRefundSummaryResponse> paymentLookup =
                lookupPaymentRefundSummaryForRefundRead(orderReturn);
        InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcLookup.value();
        PaymentClient.PaymentRefundSummaryResponse paymentSummary = paymentLookup.value();
        OrderRefundStatus refundStatus = resolveRefundStatus(
                orderReturn,
                qcSummary,
                paymentSummary,
                qcLookup.available(),
                paymentLookup.available()
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
                .refundRequired(orderReturn.getRefundRequired())
                .qcStatus(qcStatus)
                .refundStatus(refundStatus)
                .refundAmount(paymentLookup.available() && paymentSummary != null ? paymentSummary.refundAmount() : null)
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
        if (Boolean.FALSE.equals(orderReturn.getRefundRequired())) {
            throw new InvalidOrderReturnException("Refund is not required for this return");
        }
        if (ReturnStatus.COMPLETED == orderReturn.getStatus()) {
            return existingCompletedRefundResponse(orderReturn);
        }
        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Return inventory inspection is not completed");
        }

        if (!isPhysicalDispositionCompleted(returnId)) {
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary =
                    inventoryClient.getCustomerReturnQcSummary(returnId);
            if (qcSummary == null || !"COMPLETED".equals(qcSummary.qcStatus())) {
                throw new InvalidOrderReturnException("Return inventory inspection is not completed");
            }
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

        ReturnStatus oldReturnStatus = orderReturn.getStatus();
        finalizeReturnAfterRefund(orderReturn, lockedOrder, orderReturns, paymentRefund);

        orderReturnRepository.save(orderReturn);
        orderRepository.save(lockedOrder);
        UUID actorUserId = currentActorUserIdOrNull();
        writeRefundCompletedAudit(orderReturn, oldReturnStatus, actorUserId, paymentRefund);
        orderOutboxPublisher.publishReturnRefundCompletedNotification(
                orderReturn,
                oldReturnStatus,
                actorUserId,
                paymentRefund
        );

        return toRefundResponse(orderReturn, lockedOrder, paymentRefund);
    }

    @Override
    @Transactional
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

        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            throw new InvalidOrderReturnException("Only received returns can request inventory disposition");
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
                || ReturnInventoryDispositionResult.QUARANTINE == lineRequest.getResult()
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
        if (returnId == null) {
            return false;
        }
        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalse(returnId).orElse(null);
        if (orderReturn == null || orderReturn.getItems() == null) {
            return false;
        }

        if (orderReturnInventoryCommandRepository.existsByOrderReturnIdAndStatusAndIsDeletedFalse(
                returnId,
                OrderReturnInventoryCommandStatus.PENDING
        )) {
            return false;
        }

        List<OrderReturnItem> activeItems = orderReturn.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .toList();
        if (activeItems.isEmpty()) {
            return false;
        }

        List<Object[]> rows = orderReturnInventoryCommandRepository.sumQuantityByReturnIdAndCommandStatus(
                returnId,
                OrderReturnInventoryCommandStatus.PROCESSED
        );
        Map<UUID, BigDecimal> processedByItemId = rows.stream()
                .filter(row -> row != null && row.length >= 2 && row[0] instanceof UUID && row[1] instanceof BigDecimal)
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (BigDecimal) row[1],
                        BigDecimal::add
                ));

        for (OrderReturnItem item : activeItems) {
            BigDecimal processedQty = defaultZero(processedByItemId.get(item.getId()));
            BigDecimal requiredQty = defaultZero(item.getQuantity());
            if (processedQty.compareTo(requiredQty) < 0) {
                return false;
            }
            if (processedQty.compareTo(requiredQty) > 0) {
                log.warn("Physical disposition anomaly: returnItemId={} has processedQty={} exceeding requiredQty={}",
                        item.getId(), processedQty, requiredQty);
            }
        }

        return true;
    }

    @Override
    @Transactional
    public void checkAndAutoCompleteNoRefundReturn(UUID returnId) {
        if (returnId == null) {
            return;
        }

        OrderReturn orderReturn = orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId).orElse(null);
        if (orderReturn == null) {
            log.warn("Cannot auto-complete no-refund return: return not found: {}", returnId);
            return;
        }

        if (ReturnStatus.RECEIVED != orderReturn.getStatus()) {
            log.debug("Skip no-refund auto-completion: returnId={} status is not RECEIVED (current: {})",
                    returnId, orderReturn.getStatus());
            return;
        }
        if (Boolean.TRUE.equals(orderReturn.getRefundRequired())) {
            log.debug("Skip no-refund auto-completion: returnId={} requires refund", returnId);
            return;
        }

        if (!isPhysicalDispositionCompleted(returnId)) {
            log.debug("Skip no-refund auto-completion: returnId={} physical disposition is not fully completed yet", returnId);
            return;
        }

        ReturnStatus oldStatus = orderReturn.getStatus();
        orderReturn.setStatus(ReturnStatus.COMPLETED);
        OrderReturn saved = orderReturnRepository.save(orderReturn);

        Order lockedOrder = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderReturn.getOrder().getId())
                .orElse(null);
        if (lockedOrder != null) {
            List<OrderReturn> orderReturns = orderReturnRepository.findByOrderIdAndIsDeletedFalseOrderByCreatedAtDesc(lockedOrder.getId());
            lockedOrder.setReturnStatus(resolveOrderReturnStatus(lockedOrder, orderReturns));
            orderRepository.save(lockedOrder);
        }

        UUID actorUserId = currentActorUserIdOrNull();
        orderReturnHistoryWriter.writeIfAbsent(
                saved,
                OrderReturnHistoryAction.COMPLETED,
                oldStatus,
                ReturnStatus.COMPLETED,
                actorUserId,
                "Return completed after inventory processing without refund",
                null,
                null
        );

        orderOutboxPublisher.publishReturnCompletedNotification(saved, oldStatus, actorUserId);
        log.info("Auto-completed no-refund return: returnId={}, returnCode={}", saved.getId(), saved.getReturnCode());
    }

    private OrderReturn getExistingReturnForUpdate(UUID returnId) {
        return orderReturnRepository.findByIdAndIsDeletedFalseForUpdate(returnId)
                .orElseThrow(() -> new OrderNotFoundException("Return not found: " + returnId));
    }

    private void writeRefundCompletedAudit(
            OrderReturn orderReturn,
            ReturnStatus oldReturnStatus,
            UUID actorUserId,
            PaymentClient.PaymentRefundResponse paymentRefund
    ) {
        String refundTransactionId = paymentRefund.refundTransactionId() == null
                ? null
                : paymentRefund.refundTransactionId().toString();

        orderReturnHistoryWriter.writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.REFUND_COMPLETED,
                oldReturnStatus,
                ReturnStatus.COMPLETED,
                actorUserId,
                "Refund completed",
                "PAYMENT_TRANSACTION",
                refundTransactionId
        );
        orderReturnHistoryWriter.writeIfAbsent(
                orderReturn,
                OrderReturnHistoryAction.COMPLETED,
                oldReturnStatus,
                ReturnStatus.COMPLETED,
                actorUserId,
                "Return completed after refund",
                "PAYMENT_TRANSACTION",
                refundTransactionId
        );
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
        DownstreamLookupResult<Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse>> qcLookup =
                lookupQcSummariesForRefundRead(summaries, returnsById);
        DownstreamLookupResult<Map<UUID, PaymentClient.PaymentRefundSummaryResponse>> paymentLookup =
                lookupPaymentRefundSummariesForRefundRead(summaries);
        Map<UUID, InventoryClient.CustomerReturnQcSummaryResponse> qcByReturnId = qcLookup.value();
        Map<UUID, PaymentClient.PaymentRefundSummaryResponse> paymentByReturnId = paymentLookup.value();

        return summaries.stream()
                .map(summary -> {
                    OrderReturn orderReturn = returnsById.get(summary.getId());
                    InventoryClient.CustomerReturnQcSummaryResponse qcSummary = qcByReturnId.get(summary.getId());
                    PaymentClient.PaymentRefundSummaryResponse paymentSummary = paymentByReturnId.get(summary.getId());
                    OrderRefundStatus refundStatus = orderReturn == null
                            ? OrderRefundStatus.NOT_READY
                            : resolveRefundStatus(
                                    orderReturn,
                                    qcSummary,
                                    paymentSummary,
                                    qcLookup.available(),
                                    paymentLookup.available()
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

                    return OrderRefundSummaryResponse.builder()
                            .returnId(summary.getId())
                            .returnCode(summary.getReturnCode())
                            .orderId(summary.getOrderId())
                            .orderCode(summary.getOrderCode())
                            .customerName(summary.getCustomerName())
                            .customerPhone(summary.getCustomerPhone())
                            .returnCreatedAt(summary.getCreatedAt())
                            .returnStatus(summary.getStatus())
                            .refundRequired(orderReturn == null ? summary.getRefundRequired() : orderReturn.getRefundRequired())
                            .qcStatus(qcStatus)
                            .refundStatus(refundStatus)
                            .refundAmount(paymentLookup.available() && paymentSummary != null
                                    ? paymentSummary.refundAmount()
                                    : null)
                            .paymentStatus(paymentStatus)
                            .method(method)
                            .refundedAt(paymentLookup.available() && paymentSummary != null
                                    ? paymentSummary.refundedAt()
                                    : null)
                            .build();
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

    private OrderRefundStatus resolveRefundStatus(
            OrderReturn orderReturn,
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary
    ) {
        return resolveRefundStatus(orderReturn, qcSummary, paymentSummary, true, true);
    }

    private OrderRefundStatus resolveRefundStatus(
            OrderReturn orderReturn,
            InventoryClient.CustomerReturnQcSummaryResponse qcSummary,
            PaymentClient.PaymentRefundSummaryResponse paymentSummary,
            boolean inventoryAvailable,
            boolean paymentAvailable
    ) {
        if (!inventoryAvailable || !paymentAvailable) {
            return OrderRefundStatus.NOT_READY;
        }
        if (Boolean.FALSE.equals(orderReturn.getRefundRequired())) {
            return OrderRefundStatus.NOT_REQUIRED;
        }
        if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
            return OrderRefundStatus.REFUNDED;
        }
        if (paymentSummary != null && "FAILED".equals(paymentSummary.refundStatus())) {
            return OrderRefundStatus.FAILED;
        }
        boolean physicalDone = isPhysicalDispositionCompleted(orderReturn.getId())
                || (qcSummary != null && "COMPLETED".equals(qcSummary.qcStatus()));
        if (ReturnStatus.RECEIVED == orderReturn.getStatus() && physicalDone) {
            return OrderRefundStatus.PENDING;
        }
        return OrderRefundStatus.NOT_READY;
    }

    private boolean isAvailabilityStatus(int status) {
        return status == 502 || status == 503 || status == 504;
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
        if (Boolean.FALSE.equals(orderReturn.getRefundRequired())) {
            return "Refund is not required for this return";
        }
        if (paymentSummary != null && "REFUNDED".equals(paymentSummary.refundStatus())) {
            return "Return already refunded";
        }
        boolean physicalDone = isPhysicalDispositionCompleted(orderReturn.getId())
                || (qcSummary != null && "COMPLETED".equals(qcSummary.qcStatus()));
        if (ReturnStatus.RECEIVED != orderReturn.getStatus() || !physicalDone) {
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
        if (OrderStatus.COMPLETED != order.getStatus()) {
            throw new InvalidOrderReturnException("Order is not completed");
        }
        if (OrderReturnStatus.FULLY_RETURNED == order.getReturnStatus()) {
            throw new InvalidOrderReturnException("Order has already been fully returned");
        }
        if (PaymentStatus.PAID != order.getPaymentStatus()
                && PaymentStatus.PARTIALLY_REFUNDED != order.getPaymentStatus()) {
            throw new InvalidOrderReturnException("Order payment status is not eligible for return");
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
        // TEMPORARILY DISABLED:
        // Return requests currently have no maximum age.
        // Re-enable this call when the business restores the return-window policy.
        //
        // validateReturnWindow(itemRequest.getReason(), completedAt);
    }

    private void validateReturnQuantity(OrderItem orderItem, BigDecimal requestedQuantity) {
        BigDecimal purchasedQuantity = requirePositiveQuantity(orderItem.getQuantity());
        BigDecimal returnedQuantity = defaultZero(orderReturnItemRepository.sumReturnedQuantity(
                orderItem.getId(),
                ACTIVE_RETURN_STATUSES
        ));
        BigDecimal remainingQuantity = purchasedQuantity.subtract(returnedQuantity);

        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderReturnException("No remaining quantity available for return");
        }
        if (requestedQuantity.compareTo(remainingQuantity) > 0) {
            throw new InvalidOrderReturnException("Return quantity exceeds remaining returnable quantity");
        }
    }

    private void validateReturnWindow(ReturnReason reason, Instant completedAt) {
        // TEMPORARILY DISABLED:
        // Return requests currently have no maximum age.
        // Re-enable when the return-window policy is restored.
        //
        // Instant baseline = completedAt == null ? Instant.now() : completedAt;
        // Duration window = ReturnReason.CHANGE_OF_MIND == reason
        //         ? DEFAULT_CHANGE_OF_MIND_WINDOW
        //         : DEFAULT_QUALITY_WINDOW;
        //
        // if (baseline.plus(window).isBefore(Instant.now())) {
        //     throw new InvalidOrderReturnException("Return window has expired");
        // }
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
