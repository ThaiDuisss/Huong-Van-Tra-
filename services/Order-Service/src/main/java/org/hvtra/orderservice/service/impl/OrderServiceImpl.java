package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import feign.FeignException;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.commonservice.exception.FeignClientException;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.InventoryReservationFailedException;
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.exception.OutOfStockException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String DEFAULT_ORDER_TYPE = "POS";
    private static final String SORT_BUSINESS_STATUS = "businessStatus";
    private static final String SORT_INVOICE_PAYMENT = "invoicePayment";
    private static final long DRAFT_TTL_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final OrderCustomerServiceImpl orderCustomerServiceImpl;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        BigDecimal originalAmount = BigDecimal.ZERO;
        CustomerResponse customerSnapshot = loadCustomerSnapshot(request.getCustomerId());

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .tenantId(request.getTenantId())
                .branchId(request.getBranchId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .customerNameSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getFullName(),
                        request.getCustomerNameSnapshot()
                ))
                .customerPhoneSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getPhone(),
                        request.getCustomerPhoneSnapshot()
                ))
                .customerEmailSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getEmail(),
                        request.getCustomerEmailSnapshot()
                ))
                .branchNameSnapshot(request.getBranchNameSnapshot())
                .terminalNameSnapshot(request.getTerminalNameSnapshot())
                .shiftCodeSnapshot(request.getShiftCodeSnapshot())
                .cashierId(request.getCashierId())
                .cashierNameSnapshot(request.getCashierNameSnapshot())
                .recipientNameSnapshot(request.getRecipientNameSnapshot())
                .recipientPhoneSnapshot(request.getRecipientPhoneSnapshot())
                .shippingAddressSnapshot(request.getShippingAddressSnapshot())
                .shippingProviderSnapshot(request.getShippingProviderSnapshot())
                .orderType(normalizeOrderType(request.getOrderType()))
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(defaultZero(request.getDiscountAmount()))
                .shippingFee(defaultZero(request.getShippingFee()))
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .build();

        for (var itemRequest : request.getItems()) {
            var variant = catalogClient.getProductVariantPosView(itemRequest.getProductVariantId());
            BigDecimal quantity = defaultZero(itemRequest.getQuantity());
            BigDecimal actualUnitPrice = defaultZero(variant.sellingPrice());
            BigDecimal itemDiscount = defaultZero(itemRequest.getDiscountAmount());
            BigDecimal lineTotal = actualUnitPrice.multiply(quantity).subtract(itemDiscount);

            OrderItem item = OrderItem.builder()
                    .productVariantId(variant.variantId())
                    .skuSnapshot(variant.sku())
                    .productNameSnapshot(variant.productName())
                    .variantNameSnapshot(variant.variantName())
                    .quantity(quantity)
                    .originalUnitPrice(actualUnitPrice)
                    .actualUnitPrice(actualUnitPrice)
                    .discountAmount(itemDiscount)
                    .lineTotal(lineTotal)
                    .bomVersionId(itemRequest.getBomVersionId())
                    .build();

            order.addItem(item);
            originalAmount = originalAmount.add(lineTotal);
        }

        BigDecimal discountAmount = defaultZero(request.getDiscountAmount());
        BigDecimal shippingFee = defaultZero(request.getShippingFee());
        BigDecimal finalAmount = originalAmount.subtract(discountAmount).add(shippingFee);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(finalAmount);

        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(savedOrder, null, savedOrder.getStatus(), "Tạo đơn hàng", null);

        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        return toResponse(getExistingOrder(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPaymentInfoResponse getPaymentInfo(UUID orderId) {
        Order order = getExistingOrder(orderId);

        return OrderPaymentInfoResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .finalAmount(order.getFinalAmount())
                .paidAmount(order.getPaidAmount())
                .debtAmount(order.getDebtAmount())
                .build();
    }

    @Override
    @Transactional
    public OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            throw new OrderAlreadyPaidException("Đơn hàng đã được thanh toán");
        }

        BigDecimal paidAmount = defaultZero(request.getPaidAmount());
        if (paidAmount.compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }

        OrderStatus oldStatus = order.getStatus();
        confirmReservation(order.getId());
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                "Thanh toán thành công, mã thanh toán: " + request.getPaymentCode(),
                request.getChangedBy()
        );

        orderCustomerServiceImpl.updateCustomerPurchase(savedOrder.getId());

        return toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse addDraftItem(AddDraftOrderItemRequest request) {
        Instant expiresAt = nextDraftExpiresAt();
        Order order = request.getOrderId() == null
                ? createDraftOrder(request, expiresAt)
                : getEditableDraftOrder(request.getOrderId());
        order.setExpiresAt(expiresAt);

        var variant = catalogClient.getProductVariantPosView(request.getProductVariantId());
        log.info("variant: {}", variant);
        BigDecimal addQuantity = defaultPositive(request.getQuantity());
        OrderItem item = findDraftItem(order, variant.variantId(), request.getBomVersionId());
        BigDecimal oldQuantity = item == null ? BigDecimal.ZERO : defaultZero(item.getQuantity());
        BigDecimal targetQuantity = oldQuantity.add(addQuantity);
        log.info("varient{} {}", variant.variantId(), request.getProductVariantId());
        applyReservation(order, variant.variantId(), targetQuantity, request.getInventoryLocationId());

        if (item == null) {
            item = OrderItem.builder()
                    .productVariantId(variant.variantId())
                    .skuSnapshot(variant.sku())
                    .productNameSnapshot(variant.productName())
                    .variantNameSnapshot(variant.variantName())
                    .quantity(targetQuantity)
                    .originalUnitPrice(defaultZero(variant.sellingPrice()))
                    .actualUnitPrice(defaultZero(variant.sellingPrice()))
                    .discountAmount(defaultZero(request.getDiscountAmount()))
                    .bomVersionId(request.getBomVersionId())
                    .build();
            recalculateLine(item);
            order.addItem(item);
        } else {
            item.setQuantity(targetQuantity);
            recalculateLine(item);
        }

        recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateDraftItemQuantity(UUID orderId, UUID itemId, UpdateDraftOrderItemQuantityRequest request) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);
        BigDecimal targetQuantity = defaultZero(request.getQuantity());
        order.setExpiresAt(nextDraftExpiresAt());

        if (targetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return removeDraftItem(orderId, itemId);
        }

        applyReservation(order, item.getProductVariantId(), targetQuantity, null);
        item.setQuantity(targetQuantity);
        recalculateLine(item);
        recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse removeDraftItem(UUID orderId, UUID itemId) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);

        releaseItemReservation(order.getId(), item.getProductVariantId(), null);
        order.getItems().remove(item);
        order.setExpiresAt(nextDraftExpiresAt());
        recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse cancelDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        if (OrderStatus.CANCELLED == order.getStatus() || OrderStatus.EXPIRED == order.getStatus()) {
            return toResponse(order);
        }
        if (OrderStatus.DRAFT != order.getStatus()) {
            throw new InvalidOrderStatusException("Chỉ hóa đơn nháp mới được hủy bằng API này");
        }

        releaseOrderReservation(order.getId());
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(savedOrder, oldStatus, OrderStatus.CANCELLED, "Hủy hóa đơn nháp POS", null);
        return toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse checkoutDraftOrder(UUID orderId, CheckoutDraftOrderRequest request) {
        Order order = getEditableDraftOrder(orderId);
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidOrderStatusException("Chưa có sản phẩm trong đơn");
        }

        recalculateOrderTotals(order);
        BigDecimal paidAmount = defaultZero(request.getPaidAmount());
        if (paidAmount.compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }

        confirmReservation(order.getId());
        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                "Thanh toán POS trực tiếp, mã thanh toán: " + request.getPaymentCode(),
                request.getChangedBy());
        orderCustomerServiceImpl.updateCustomerPurchase(savedOrder.getId());
        return toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrders(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200)
        );

        Specification<Order> filters = orderSpecification(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate
        );

        return orderRepository.findAll(
                withBusinessOrder(filters, normalizeOrderSort(sort)),
                pageRequest
        ).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate) {
        Specification<Order> dateRange = orderSpecification(null, null, null, null, fromDate, toDate);

        return OrderStatsResponse.builder()
                .total(orderRepository.count(dateRange))
                .pending(countByStatuses(fromDate, toDate, List.of(OrderStatus.DRAFT, OrderStatus.PENDING_PAYMENT)))
                .processing(countByStatuses(fromDate, toDate, List.of(OrderStatus.PAYMENT_CONFIRMED)))
                .shipping(0)
                .completed(countByStatuses(fromDate, toDate, List.of(OrderStatus.COMPLETED)))
                .returnedOrRefunded(countByPaymentStatuses(
                        fromDate,
                        toDate,
                        List.of(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED)
                ))
                .unpaidInvoices(countByPaymentStatuses(
                        fromDate,
                        toDate,
                        List.of(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT)
                ))
                .paidInvoices(countByPaymentStatuses(fromDate, toDate, List.of(PaymentStatus.PAID)))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TodaySalesItemResponse> getTodaySales(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethod,
            int page,
            int size
    ) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        List<TodaySalesItemResponse> filteredItems = orderRepository.findAll(
                        orderSpecification(null, status, paymentStatus, null, today, today),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toTodaySalesItemResponse)
                .filter(item -> paymentMethodMatches(item, paymentMethod))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), filteredItems.size());
        int end = Math.min(start + pageable.getPageSize(), filteredItems.size());

        return new PageImpl<>(filteredItems.subList(start, end), pageable, filteredItems.size());
    }

    @Override
    @Transactional(readOnly = true)
    public TodaySalesSummaryResponse getTodaySalesSummary() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<Order> todayOrders = orderRepository.findAll(
                orderSpecification(null, null, null, null, today, today)
        );

        long totalOrders = todayOrders.size();
        long completedOrders = todayOrders.stream().filter(this::isCompletedSale).count();
        long pendingPaymentOrders = todayOrders.stream().filter(this::isPendingPaymentSale).count();

        // Doanh thu chỉ tính đơn đã thanh toán/hoàn thành và không bị hủy.
        BigDecimal todayRevenue = todayOrders.stream()
                .filter(this::isCompletedSale)
                .map(order -> defaultZero(order.getFinalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Chờ thu chỉ tính đơn chưa thanh toán và chưa hủy.
        BigDecimal pendingAmount = todayOrders.stream()
                .filter(this::isPendingPaymentSale)
                .map(order -> defaultZero(order.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0
                        ? defaultZero(order.getDebtAmount())
                        : defaultZero(order.getFinalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageOrderValue = completedOrders == 0
                ? BigDecimal.ZERO
                : todayRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP);

        return TodaySalesSummaryResponse.builder()
                .todayRevenue(todayRevenue)
                .totalOrders(totalOrders)
                .completedOrders(completedOrders)
                .pendingPaymentOrders(pendingPaymentOrders)
                .averageOrderValue(averageOrderValue)
                .pendingAmount(pendingAmount)
                .build();
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = getExistingOrder(orderId);
        OrderStatus oldStatus = order.getStatus();

        if (oldStatus == request.getStatus()) {
            return toResponse(order);
        }

        order.setStatus(request.getStatus());
        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(savedOrder, oldStatus, request.getStatus(), request.getNote(), null);

        return toResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, CancelOrderRequest request) {
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus() || OrderStatus.COMPLETED == order.getStatus()) {
            throw new InvalidOrderStatusException(
                    "Đơn hàng đã thanh toán hoặc đã hoàn thành, không thể hủy trực tiếp. Vui lòng dùng luồng hoàn tiền/hoàn hàng."
            );
        }
        if (OrderStatus.CANCELLED == order.getStatus()) {
            throw new InvalidOrderStatusException("Đơn hàng đã được hủy trước đó");
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(savedOrder, oldStatus, OrderStatus.CANCELLED, request.getReason(), null);

        return toResponse(savedOrder);
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private Order createDraftOrder(AddDraftOrderItemRequest request, Instant expiresAt) {
        CustomerResponse customerSnapshot = loadCustomerSnapshot(request.getCustomerId());
        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .tenantId(request.getTenantId())
                .branchId(request.getBranchId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .customerNameSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getFullName(),
                        request.getCustomerNameSnapshot()))
                .customerPhoneSnapshot(firstNonBlank(
                        customerSnapshot == null ? null : customerSnapshot.getPhone(),
                        request.getCustomerPhoneSnapshot()))
                .customerEmailSnapshot(customerSnapshot == null ? null : customerSnapshot.getEmail())
                .branchNameSnapshot(request.getBranchNameSnapshot())
                .terminalNameSnapshot(request.getTerminalNameSnapshot())
                .shiftCodeSnapshot(request.getShiftCodeSnapshot())
                .cashierId(request.getCashierId())
                .cashierNameSnapshot(request.getCashierNameSnapshot())
                .orderType(DEFAULT_ORDER_TYPE)
                .status(OrderStatus.DRAFT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .expiresAt(expiresAt)
                .build();
        Order savedOrder = orderRepository.saveAndFlush(order);
        saveStatusHistory(savedOrder, null, OrderStatus.DRAFT, "Tạo hóa đơn nháp POS", request.getCashierId());
        return savedOrder;
    }

    private Order getEditableDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        if (OrderStatus.DRAFT != order.getStatus()) {
            throw new InvalidOrderStatusException("ORDER_NOT_DRAFT");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOrderStatusException("ORDER_EXPIRED");
        }
        return order;
    }

    private OrderItem findDraftItem(Order order, UUID productVariantId, UUID bomVersionId) {
        if (order.getItems() == null) {
            return null;
        }
        return order.getItems().stream()
                .filter(item -> item.getProductVariantId().equals(productVariantId))
                .filter(item -> bomVersionMatches(item.getBomVersionId(), bomVersionId))
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

    private Instant nextDraftExpiresAt() {
        return Instant.now().plusSeconds(DRAFT_TTL_MINUTES * 60);
    }

    private BigDecimal defaultPositive(BigDecimal value) {
        BigDecimal normalized = defaultZero(value);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderStatusException("Số lượng phải lớn hơn 0");
        }
        return normalized;
    }

    private void recalculateLine(OrderItem item) {
        BigDecimal quantity = defaultZero(item.getQuantity());
        BigDecimal unitPrice = defaultZero(item.getActualUnitPrice());
        BigDecimal discountAmount = defaultZero(item.getDiscountAmount());
        BigDecimal lineTotal = unitPrice.multiply(quantity).subtract(discountAmount);
        item.setLineTotal(lineTotal.max(BigDecimal.ZERO));
    }

    private void recalculateOrderTotals(Order order) {
        BigDecimal originalAmount = order.getItems() == null
                ? BigDecimal.ZERO
                : order.getItems().stream()
                        .peek(this::recalculateLine)
                        .map(item -> defaultZero(item.getLineTotal()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = defaultZero(order.getDiscountAmount());
        BigDecimal shippingFee = defaultZero(order.getShippingFee());
        BigDecimal finalAmount = originalAmount.subtract(discountAmount).add(shippingFee).max(BigDecimal.ZERO);

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        if (PaymentStatus.PAID != order.getPaymentStatus()) {
            order.setPaidAmount(BigDecimal.ZERO);
            order.setDebtAmount(finalAmount);
        }
    }

    private void applyReservation(Order order, UUID productVariantId, BigDecimal targetQuantity, UUID locationId) {
        try {
            inventoryClient.applyReservation(InventoryClient.ApplyInventoryReservationRequest.builder()
                    .orderId(order.getId())
                    .branchId(order.getBranchId())
                    .productVariantId(productVariantId)
                    .locationId(locationId)
                    .targetQuantity(targetQuantity)
                    .expiresAt(order.getExpiresAt())
                    .build());
        } catch (FeignException.Conflict ex) {
            throw new OutOfStockException("Không đủ tồn kho");
        } catch (FeignException.NotFound ex) {
            throw new InventoryReservationFailedException("Không tìm thấy tồn kho để giữ chỗ");
        } catch (FeignClientException ex) {
            if (isOutOfStock(ex)) {
                throw new OutOfStockException("Không đủ tồn kho");
            }
            if (ex.getStatus() == 404) {
                throw new InventoryReservationFailedException("Không tìm thấy tồn kho để giữ chỗ");
            }
            throw new InventoryReservationFailedException("Không thể giữ chỗ tồn kho: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể giữ chỗ tồn kho: " + ex.getMessage());
        }
    }

    private void releaseItemReservation(UUID orderId, UUID productVariantId, UUID locationId) {
        try {
            inventoryClient.releaseItem(InventoryClient.ReleaseInventoryReservationItemRequest.builder()
                    .orderId(orderId)
                    .productVariantId(productVariantId)
                    .locationId(locationId)
                    .build());
        } catch (FeignClientException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho: " + ex.getMessage());
        }
    }

    private void releaseOrderReservation(UUID orderId) {
        try {
            inventoryClient.releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder()
                    .orderId(orderId)
                    .build());
        } catch (FeignClientException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho của đơn: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể nhả giữ chỗ tồn kho của đơn: " + ex.getMessage());
        }
    }

    private void confirmReservation(UUID orderId) {
        try {
            inventoryClient.confirmOrder(InventoryClient.ConfirmInventoryReservationOrderRequest.builder()
                    .orderId(orderId)
                    .build());
        } catch (FeignException.Conflict ex) {
            throw new OutOfStockException("Không đủ tồn kho để xác nhận đơn");
        } catch (FeignClientException ex) {
            if (isOutOfStock(ex)) {
                throw new OutOfStockException("Không đủ tồn kho để xác nhận đơn");
            }
            throw new InventoryReservationFailedException("Không thể xác nhận giữ chỗ tồn kho: " + ex.getMessage());
        } catch (FeignException ex) {
            throw new InventoryReservationFailedException("Không thể xác nhận giữ chỗ tồn kho: " + ex.getMessage());
        }
    }

    private boolean isOutOfStock(FeignClientException ex) {
        return ex.getStatus() == 409
                || (ex.getMessage() != null && ex.getMessage().contains("OUT_OF_STOCK"))
                || (ex.getResponseBody() != null && ex.getResponseBody().contains("OUT_OF_STOCK"));
    }

    private void saveStatusHistory(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reason,
            UUID changedBy
    ) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .reason(reason)
                .changedBy(changedBy)
                .changedAt(Instant.now())
                .build();

        orderStatusHistoryRepository.save(history);
    }

    private Specification<Order> orderSpecification(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("orderCode")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerNameSnapshot")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerPhoneSnapshot")), pattern)
                ));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                if (PaymentStatus.UNPAID == paymentStatus) {
                    predicates.add(root.get("paymentStatus").in(PaymentStatus.UNPAID, PaymentStatus.WAITING_PAYMENT));
                } else if (PaymentStatus.REFUNDED == paymentStatus) {
                    predicates.add(root.get("paymentStatus").in(PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("paymentStatus"), paymentStatus));
                }
            }
            if (orderType != null && !orderType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("orderType"), normalizeOrderType(orderType)));
            }
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(fromDate)));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), startOfDay(toDate.plusDays(1))));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<Order> withBusinessOrder(Specification<Order> filters, String sort) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = filters.toPredicate(root, query, criteriaBuilder);
            applyDefaultOrder(root, query, criteriaBuilder, sort);
            return predicate;
        };
    }

    private String normalizeOrderSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_BUSINESS_STATUS;
        }

        if (SORT_INVOICE_PAYMENT.equalsIgnoreCase(sort)) {
            return SORT_INVOICE_PAYMENT;
        }

        return SORT_BUSINESS_STATUS;
    }

    private void applyDefaultOrder(
            Root<Order> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            String sort
    ) {
        if (sort == null || sort.isBlank() || isCountQuery(query)) {
            return;
        }

        Expression<Integer> priority = SORT_INVOICE_PAYMENT.equalsIgnoreCase(sort)
                ? invoicePaymentPriority(root, criteriaBuilder)
                : orderStatusPriority(root, criteriaBuilder);

        query.orderBy(
                criteriaBuilder.asc(priority),
                criteriaBuilder.desc(root.get("createdAt"))
        );
    }

    private boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> resultType = query.getResultType();
        return Long.class.equals(resultType) || long.class.equals(resultType);
    }

    private Expression<Integer> orderStatusPriority(Root<Order> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PENDING_PAYMENT), 1)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PAYMENT_CONFIRMED), 2)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.COMPLETED), 3)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.REFUNDED), 4)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.PARTIALLY_REFUNDED), 4)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.PAYMENT_FAILED), 5)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.EXPIRED), 6)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.CANCELLED), 7)
                .when(criteriaBuilder.equal(root.get("status"), OrderStatus.DRAFT), 8)
                .otherwise(99);
    }

    private Expression<Integer> invoicePaymentPriority(Root<Order> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.UNPAID), 1)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.WAITING_PAYMENT), 1)
                .when(criteriaBuilder.equal(root.get("paymentStatus"), PaymentStatus.PAID), 2)
                .otherwise(99);
    }

    private long countByStatuses(LocalDate fromDate, LocalDate toDate, List<OrderStatus> statuses) {
        return orderRepository.count((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            predicates.add(root.get("status").in(statuses));
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(fromDate)));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), startOfDay(toDate.plusDays(1))));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        });
    }

    private long countByPaymentStatuses(LocalDate fromDate, LocalDate toDate, List<PaymentStatus> statuses) {
        return orderRepository.count((root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            predicates.add(root.get("paymentStatus").in(statuses));
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startOfDay(fromDate)));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdAt"), startOfDay(toDate.plusDays(1))));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        });
    }

    private OrderResponse toResponse(Order order) {
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order.getId());
        BigDecimal changeAmount = paymentInfo == null ? BigDecimal.ZERO : defaultZero(paymentInfo.changeAmount());
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);
        LocalDateTime createdAt = toLocalDateTime(auditValue(order, "getCreatedAt"));

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .tenantId(order.getTenantId())
                .branchId(order.getBranchId())
                .terminalId(order.getTerminalId())
                .shiftId(order.getShiftId())
                .customerId(order.getCustomerId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(paymentMethod)
                .paymentMethodLabel(toPaymentMethodLabel(paymentMethod))
                .originalAmount(order.getOriginalAmount())
                .subTotal(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalAmount(order.getFinalAmount())
                .paidAmount(order.getPaidAmount())
                .debtAmount(order.getDebtAmount())
                .changeAmount(changeAmount)
                .expiresAt(order.getExpiresAt())
                .items(order.getItems().stream().map(this::toItemResponse).toList())
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .customerEmail(order.getCustomerEmailSnapshot())
                .branchName(order.getBranchNameSnapshot())
                .terminalName(order.getTerminalNameSnapshot())
                .shiftCode(order.getShiftCodeSnapshot())
                .cashierId(order.getCashierId())
                .cashierName(order.getCashierNameSnapshot())
                .recipientName(order.getRecipientNameSnapshot())
                .recipientPhone(order.getRecipientPhoneSnapshot())
                .shippingAddress(order.getShippingAddressSnapshot())
                .shippingProvider(order.getShippingProviderSnapshot())
                .orderTypeLabel(toOrderTypeLabel(order.getOrderType()))
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .createdBy(toAuditString(auditValue(order, "getCreatedBy")))
                .createdByName(order.getCashierNameSnapshot())
                .createdAt(createdAt)
                .soldAt(createdAt)
                .updatedBy(toAuditString(auditValue(order, "getUpdatedBy")))
                .updatedAt(toLocalDateTime(auditValue(order, "getUpdatedAt")))
                .statusHistories(toStatusHistoryResponses(order.getId()))
                .build();
    }

    private OrderSummaryResponse toSummaryResponse(Order order) {
        return OrderSummaryResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .orderType(order.getOrderType())
                .orderTypeLabel(toOrderTypeLabel(order.getOrderType()))
                .status(order.getStatus())
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatus(order.getPaymentStatus())
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .finalAmount(order.getFinalAmount())
                .createdAt(toLocalDateTime(auditValue(order, "getCreatedAt")))
                .createdBy(toAuditString(auditValue(order, "getCreatedBy")))
                .createdByName(order.getCashierNameSnapshot())
                .cashierId(order.getCashierId())
                .cashierName(order.getCashierNameSnapshot())
                .build();
    }

    private TodaySalesItemResponse toTodaySalesItemResponse(Order order) {
        LocalDateTime createdAt = toLocalDateTime(auditValue(order, "getCreatedAt"));
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order.getId());
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);

        return TodaySalesItemResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .soldAt(createdAt)
                .createdAt(createdAt)
                .customerName(order.getCustomerNameSnapshot())
                .customerPhone(order.getCustomerPhoneSnapshot())
                .cashierName(order.getCashierNameSnapshot())
                .totalItems(totalItems(order))
                .finalAmount(order.getFinalAmount())
                .paymentMethod(paymentMethod)
                .paymentMethodLabel(toPaymentMethodLabel(paymentMethod))
                .status(order.getStatus())
                .statusLabel(toOrderStatusLabel(order.getStatus()))
                .paymentStatus(order.getPaymentStatus())
                .paymentStatusLabel(toPaymentStatusLabel(order.getPaymentStatus()))
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariantId())
                .skuSnapshot(item.getSkuSnapshot())
                .sku(item.getSkuSnapshot())
                .productNameSnapshot(item.getProductNameSnapshot())
                .productName(item.getProductNameSnapshot())
                .variantNameSnapshot(item.getVariantNameSnapshot())
                .quantity(item.getQuantity())
                .originalUnitPrice(item.getOriginalUnitPrice())
                .actualUnitPrice(item.getActualUnitPrice())
                .unitPrice(item.getActualUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .lineTotal(item.getLineTotal())
                .bomVersionId(item.getBomVersionId())
                .build();
    }

    private List<OrderStatusHistoryResponse> toStatusHistoryResponses(UUID orderId) {
        return orderStatusHistoryRepository.findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(orderId)
                .stream()
                .map(this::toStatusHistoryResponse)
                .toList();
    }

    private OrderStatusHistoryResponse toStatusHistoryResponse(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .oldStatus(history.getFromStatus())
                .newStatus(history.getToStatus())
                .oldStatusLabel(toOrderStatusLabel(history.getFromStatus()))
                .newStatusLabel(toOrderStatusLabel(history.getToStatus()))
                .note(history.getReason())
                .createdBy(toAuditString(history.getChangedBy()))
                .createdByName(null)
                .createdAt(toLocalDateTime(history.getChangedAt()))
                .build();
    }

    private CustomerResponse loadCustomerSnapshot(UUID customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            return orderCustomerServiceImpl.getCustomerById(customerId);
        } catch (RuntimeException ex) {
            log.warn("Không lấy được snapshot khách hàng customerId={}: {}", customerId, ex.getMessage());
            return null;
        }
    }

    private String toOrderTypeLabel(String orderType) {
        if (orderType == null) {
            return null;
        }
        return switch (orderType.toUpperCase(Locale.ROOT)) {
            case "POS" -> "Bán tại quầy";
            case "ONLINE" -> "Đơn online";
            case "RETURN" -> "Hoàn hàng";
            case "REFUND" -> "Hoàn tiền";
            case "EXCHANGE" -> "Đổi hàng";
            default -> orderType;
        };
    }

    private String toOrderStatusLabel(OrderStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case DRAFT -> "Nháp";
            case PENDING_PAYMENT -> "Chờ thanh toán";
            case PAYMENT_CONFIRMED -> "Chờ duyệt";
            case COMPLETED -> "Đã hoàn thành";
            case CANCELLED -> "Đã hủy";
            case EXPIRED -> "Đã hết hạn";
            case PAYMENT_FAILED -> "Thanh toán thất bại";
        };
    }

    private String toPaymentStatusLabel(PaymentStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UNPAID, WAITING_PAYMENT -> "Chưa thanh toán";
            case PAID -> "Đã thanh toán";
            case FAILED -> "Thanh toán thất bại";
            case REFUNDED -> "Đã hoàn tiền";
            case PARTIALLY_REFUNDED -> "Hoàn tiền một phần";
        };
    }

    private String toPaymentMethodLabel(String paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        return switch (paymentMethod) {
            case "CASH" -> "Tiền mặt";
            case "BANK_TRANSFER", "QR_CODE" -> "Chuyển khoản";
            case "DEBT" -> "Công nợ";
            case "CARD" -> "Thẻ";
            case "E_WALLET" -> "Ví điện tử";
            case "COMBINED" -> "Kết hợp";
            default -> paymentMethod;
        };
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isCompletedSale(Order order) {
        return OrderStatus.CANCELLED != order.getStatus()
                && (PaymentStatus.PAID == order.getPaymentStatus() || OrderStatus.COMPLETED == order.getStatus());
    }

    private boolean isPendingPaymentSale(Order order) {
        return OrderStatus.CANCELLED != order.getStatus()
                && (PaymentStatus.UNPAID == order.getPaymentStatus()
                || PaymentStatus.WAITING_PAYMENT == order.getPaymentStatus()
                || OrderStatus.PENDING_PAYMENT == order.getStatus());
    }

    private BigDecimal totalItems(Order order) {
        if (order.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return order.getItems().stream()
                .map(item -> defaultZero(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean paymentMethodMatches(TodaySalesItemResponse item, String paymentMethod) {
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
        return normalizedPaymentMethod == null
                || normalizedPaymentMethod.equals(normalizePaymentMethod(item.getPaymentMethod()));
    }

    private PaymentClient.PaymentOrderInfoResponse fetchPaymentInfo(UUID orderId) {
        try {
            return paymentClient.getPaymentByOrderId(orderId);
        } catch (RuntimeException ex) {
            log.debug("Không lấy được payment info cho orderId={}: {}", orderId, ex.getMessage());
            return null;
        }
    }

    private String resolvePaymentMethod(Order order, PaymentClient.PaymentOrderInfoResponse paymentInfo) {
        if (paymentInfo != null && paymentInfo.method() != null) {
            return normalizePaymentMethod(paymentInfo.method());
        }
        if (defaultZero(order.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0 || isPendingPaymentSale(order)) {
            return "DEBT";
        }
        return PaymentStatus.PAID == order.getPaymentStatus() ? "CASH" : null;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return null;
        }
        String normalized = paymentMethod.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TRANSFER", "BANK_TRANSFER", "QR_CODE" -> "BANK_TRANSFER";
            case "CASH" -> "CASH";
            case "DEBT", "CREDIT" -> "DEBT";
            case "CARD" -> "CARD";
            case "E_WALLET" -> "E_WALLET";
            case "COMBINED" -> "COMBINED";
            default -> normalized;
        };
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) {
            return DEFAULT_ORDER_TYPE;
        }
        return orderType.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String toAuditString(Object value) {
        return value == null ? null : value.toString();
    }

    private Object auditValue(Object entity, String getterName) {
        if (entity == null) {
            return null;
        }
        try {
            Method method = entity.getClass().getMethod(getterName);
            return method.invoke(entity);
        } catch (ReflectiveOperationException ex) {
            log.warn("Không đọc được audit field {} từ {}: {}", getterName, entity.getClass().getName(), ex.getMessage());
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private String generateOrderCode() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
