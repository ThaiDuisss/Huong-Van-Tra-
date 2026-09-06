package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderReturn;
import org.hvtra.orderservice.domain.entity.OrderReturnItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnReason;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CreateOrderReturnItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.exception.InvalidOrderReturnException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderReturnItemRepository;
import org.hvtra.orderservice.repository.OrderReturnRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.mapper.OrderReturnMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderReturnServiceImpl implements OrderReturnService {

    private static final List<ReturnStatus> ACTIVE_RETURN_STATUSES =
            List.of(ReturnStatus.CREATED, ReturnStatus.COMPLETED);
    private static final Duration DEFAULT_CHANGE_OF_MIND_WINDOW = Duration.ofDays(7);
    private static final Duration DEFAULT_QUALITY_WINDOW = Duration.ofDays(14);

    private final OrderRepository orderRepository;
    private final OrderReturnRepository orderReturnRepository;
    private final OrderReturnItemRepository orderReturnItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderReturnMapper orderReturnMapper;

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

    private void validateOrderEligibility(Order order) {
        if (OrderStatus.COMPLETED != order.getStatus() || PaymentStatus.PAID != order.getPaymentStatus()) {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
