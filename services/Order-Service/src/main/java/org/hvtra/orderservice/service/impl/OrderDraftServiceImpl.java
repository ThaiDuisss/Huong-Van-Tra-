package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.service.OrderInventoryReservationService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.support.ProductSnapshotResolver;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDraftServiceImpl implements OrderDraftService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderDateTimeHelper orderDateTimeHelper;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final CustomerSnapshotResolver customerSnapshotResolver;
    private final ProductSnapshotResolver productSnapshotResolver;
    private final OrderInventoryReservationService inventoryReservationService;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    @Override
    @Transactional
    public OrderResponse addDraftItem(AddDraftOrderItemRequest request) {
        Instant expiresAt = orderDateTimeHelper.nextDraftExpiresAt();
        Order order = request.getOrderId() == null
                ? createDraftOrder(request, expiresAt)
                : getEditableDraftOrder(request.getOrderId());
        order.setExpiresAt(expiresAt);

        var variant = productSnapshotResolver.resolvePosView(request.getProductVariantId());
        BigDecimal addQuantity = orderValidator.requirePositiveQuantity(request.getQuantity());
        OrderItem item = findDraftItem(
                order,
                variant.variantId(),
                request.getBomVersionId(),
                request.getNote()
        );
        BigDecimal oldQuantity = item == null
                ? BigDecimal.ZERO
                : orderAmountCalculator.defaultZero(item.getQuantity());
        BigDecimal targetQuantity = oldQuantity.add(addQuantity);
        validateSellableQuantity(variant.sellableQuantity(), targetQuantity);

        if (item == null) {
            item = orderItemMapper.toDraftOrderItem(
                    variant,
                    targetQuantity,
                    orderAmountCalculator.defaultZero(variant.sellingPrice()),
                    orderAmountCalculator.defaultZero(request.getDiscountAmount()),
                    request.getBomVersionId(),
                    request.getNote()
            );
            orderAmountCalculator.recalculateLine(item);
            order.addItem(item);
        } else {
            item.setQuantity(targetQuantity);
            orderAmountCalculator.recalculateLine(item);
        }

        orderAmountCalculator.recalculateOrderTotals(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateDraftItemQuantity(
            UUID orderId,
            UUID itemId,
            UpdateDraftOrderItemQuantityRequest request
    ) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);
        BigDecimal targetQuantity = orderAmountCalculator.defaultZero(request.getQuantity());
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());

        if (targetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return removeDraftItemInCurrentTransaction(orderId, itemId);
        }

        var variant = productSnapshotResolver.resolvePosView(item.getProductVariantId());
        validateSellableQuantity(variant.sellableQuantity(), targetQuantity);
        item.setQuantity(targetQuantity);
        orderAmountCalculator.recalculateLine(item);
        orderAmountCalculator.recalculateOrderTotals(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse removeDraftItem(UUID orderId, UUID itemId) {
        return removeDraftItemInCurrentTransaction(orderId, itemId);
    }

    @Override
    @Transactional
    public OrderResponse cancelDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        if (OrderStatus.CANCELLED == order.getStatus() || OrderStatus.EXPIRED == order.getStatus()) {
            return orderResponseAssembler.toResponse(order);
        }
        orderValidator.validateDraftCancellation(order);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, OrderStatus.CANCELLED, "Hủy hóa đơn nháp POS", null);
        return orderResponseAssembler.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse checkoutDraftOrder(UUID orderId, CheckoutDraftOrderRequest request) {
        Order order = getEditableDraftOrder(orderId);
        orderValidator.validateDraftHasItems(order);

        if (request.getDiscountAmount() != null) {
            order.setDiscountAmount(
                    orderAmountCalculator.defaultZero(request.getDiscountAmount()).max(BigDecimal.ZERO)
            );
        }
        orderAmountCalculator.recalculateOrderTotals(order);
        String paymentMethod = orderResponseAssembler.normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            throw new InvalidPaymentAmountException("Phương thức thanh toán không hợp lệ");
        }

        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(
                request.getPaymentCode(),
                orderCodeGenerator.generatePosPaymentCode(order, paymentMethod)
        ));
        Order savedOrder = orderRepository.save(order);

        PaymentResponse payment = orderCheckoutPaymentProcessor.checkoutPayment(
                savedOrder,
                paymentMethod,
                request.getPaidAmount(),
                request.getChangedBy()
        );

        Order checkoutOrder = orderRepository.save(savedOrder);
        return orderResponseAssembler.toResponse(checkoutOrder, payment);
    }

    private OrderResponse removeDraftItemInCurrentTransaction(UUID orderId, UUID itemId) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);

        order.getItems().remove(item);
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());
        orderAmountCalculator.recalculateOrderTotals(order);
        return orderResponseAssembler.toResponse(orderRepository.save(order));
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private void validateSellableQuantity(BigDecimal sellableQuantity, BigDecimal targetQuantity) {
        BigDecimal safeSellable = sellableQuantity == null ? BigDecimal.ZERO : sellableQuantity;
        BigDecimal safeTarget = targetQuantity == null ? BigDecimal.ZERO : targetQuantity;
        if (safeTarget.compareTo(safeSellable) > 0) {
            throw new InvalidOrderStatusException("Số lượng bán vượt quá số lượng có thể bán");
        }
    }

    private Order createDraftOrder(AddDraftOrderItemRequest request, Instant expiresAt) {
        CustomerResponse customerSnapshot = customerSnapshotResolver.resolve(request.getCustomerId());
        Order order = orderMapper.toDraftOrder(
                request,
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                expiresAt
        );
        Order savedOrder = orderRepository.saveAndFlush(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                null,
                OrderStatus.DRAFT,
                "Tạo hóa đơn nháp POS",
                request.getCashierId()
        );
        return savedOrder;
    }

    private Order getEditableDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        orderValidator.validateEditableDraft(order, Instant.now());
        return order;
    }

    private OrderItem findDraftItem(
            Order order,
            UUID productVariantId,
            UUID bomVersionId,
            String note
    ) {
        if (order.getItems() == null) {
            return null;
        }
        return order.getItems().stream()
                .filter(item -> item.getProductVariantId().equals(productVariantId))
                .filter(item -> bomVersionMatches(item.getBomVersionId(), bomVersionId))
                .filter(item -> notesMatch(item.getNote(), note))
                .findFirst()
                .orElse(null);
    }

    private OrderItem getOrderItem(Order order, UUID itemId) {
        return order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy dòng hàng: " + itemId));
    }

    private boolean bomVersionMatches(UUID current, UUID requested) {
        return current == null ? requested == null : current.equals(requested);
    }

    private boolean notesMatch(String current, String requested) {
        String currentNote = normalizeNote(current);
        String requestedNote = normalizeNote(requested);
        return currentNote == null ? requestedNote == null : currentNote.equals(requestedNote);
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
