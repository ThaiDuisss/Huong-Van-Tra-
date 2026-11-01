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
import org.hvtra.orderservice.client.InventoryClient;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderDraftServiceImpl implements OrderDraftService {

    /** Variant hệ thống "Trà đóng theo yêu cầu" bên Catalog (migration V261019100000). */
    private static final String CUSTOM_BUNDLE_SKU = "CUSTOM-BUNDLE";

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
    private final InventoryClient inventoryClient;

    @Override
    @Transactional
    public OrderResponse addDraftItem(AddDraftOrderItemRequest request) {
        log.info("oke");
        Instant expiresAt = orderDateTimeHelper.nextDraftExpiresAt();
        Order order = request.getOrderId() == null
                ? createDraftOrder(request, expiresAt)
                : getEditableDraftOrder(request.getOrderId());
        order.setExpiresAt(expiresAt);

        var variant = productSnapshotResolver.resolvePosView(request.getProductVariantId());
        if (CUSTOM_BUNDLE_SKU.equals(variant.sku())) {
            throw new InvalidOrderStatusException("Trà đóng theo yêu cầu phải thêm bằng chức năng đóng trà");
        }
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
        if (!Boolean.TRUE.equals(request.getAllowBackorder())) {
            validateSellableQuantity(order, item, variant.variantId(), targetQuantity);
        }

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
        if (item.isCustomBundle() && targetQuantity.compareTo(BigDecimal.ZERO) > 0) {
            throw new InvalidOrderStatusException("Mỗi dòng trà đóng theo yêu cầu có số lượng 1; sửa nguyên liệu bằng chức năng đóng trà");
        }
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());

        if (targetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return removeDraftItemInCurrentTransaction(orderId, itemId);
        }

        var variant = productSnapshotResolver.resolvePosView(item.getProductVariantId());
        if (!Boolean.TRUE.equals(request.getAllowBackorder())) {
            validateSellableQuantity(order, item, variant.variantId(), targetQuantity);
        }
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

        boolean depositRequested = request.getDepositAmount() != null
                && request.getDepositAmount().compareTo(BigDecimal.ZERO) > 0;
        PaymentResponse payment = Boolean.TRUE.equals(request.getAllowBackorder()) || depositRequested
                ? orderCheckoutPaymentProcessor.checkoutBackorderPayment(
                        savedOrder,
                        paymentMethod,
                        request.getPaidAmount(),
                        request.getChangedBy(),
                        request.getDepositAmount()
                )
                : orderCheckoutPaymentProcessor.checkoutPayment(
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

    /**
     * Số có thể bán = tồn thực − đang giữ chỗ, lấy từ Inventory (cộng cả kệ và kho).
     * Không dùng product_variants.sellable_quantity của Catalog: đó là cấu hình sản xuất của admin.
     * So trên tổng số đặt của mặt hàng trong đơn, vì một mặt hàng có thể nằm ở nhiều dòng (khác ghi chú).
     */
    private void validateSellableQuantity(Order order, OrderItem editedItem, UUID variantId, BigDecimal targetQuantity) {
        BigDecimal otherLines = order.getItems() == null ? BigDecimal.ZERO : order.getItems().stream()
                .filter(line -> line != editedItem && variantId.equals(line.getProductVariantId()))
                .map(line -> line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requested = otherLines.add(targetQuantity == null ? BigDecimal.ZERO : targetQuantity);
        if (requested.compareTo(availableToSell(variantId)) > 0) {
            throw new InvalidOrderStatusException("Số lượng bán vượt quá số lượng có thể bán");
        }
    }

    private BigDecimal availableToSell(UUID variantId) {
        List<InventoryClient.StockVariantBatchResponse> stocks = inventoryClient.getStocksByVariantIds(
                new InventoryClient.StockVariantBatchRequest(List.of(variantId)));
        if (stocks == null) {
            return BigDecimal.ZERO;
        }
        return stocks.stream()
                .filter(stock -> variantId.equals(stock.variantId()) && stock.availableQuantity() != null)
                .map(InventoryClient.StockVariantBatchResponse::availableQuantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
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
