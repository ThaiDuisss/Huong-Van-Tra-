package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderStatusHistoryResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderInventoryReservationService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;
    private final OrderCustomerServiceImpl orderCustomerServiceImpl;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderInventoryReservationService inventoryReservationService;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderDateTimeHelper orderDateTimeHelper;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal discountAmount = orderAmountCalculator.defaultZero(request.getDiscountAmount());
        BigDecimal shippingFee = orderAmountCalculator.defaultZero(request.getShippingFee());
        CustomerResponse customerSnapshot = loadCustomerSnapshot(request.getCustomerId());
        Order order = orderMapper.toOrder(
                request,
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                normalizeOrderType(request.getOrderType()),
                discountAmount,
                shippingFee
        );

        for (var itemRequest : request.getItems()) {
            var variant = catalogClient.getProductVariantPosView(itemRequest.getProductVariantId());
            BigDecimal quantity = orderAmountCalculator.defaultZero(itemRequest.getQuantity());
            BigDecimal actualUnitPrice = orderAmountCalculator.defaultZero(variant.sellingPrice());
            BigDecimal itemDiscount = orderAmountCalculator.defaultZero(itemRequest.getDiscountAmount());
            BigDecimal lineTotal = orderAmountCalculator.calculateCreationLineTotal(
                    actualUnitPrice,
                    quantity,
                    itemDiscount
            );

            OrderItem item = orderItemMapper.toOrderItem(
                    variant,
                    quantity,
                    actualUnitPrice,
                    itemDiscount,
                    lineTotal,
                    itemRequest.getBomVersionId()
            );

            order.addItem(item);
            originalAmount = originalAmount.add(lineTotal);
        }

        orderAmountCalculator.applyCreatedOrderTotals(
                order,
                originalAmount,
                discountAmount,
                shippingFee
        );

        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(savedOrder, null, savedOrder.getStatus(), "Tạo đơn hàng", null);

        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            return toResponse(savedOrder);
        }
        PaymentResponse payment = null;
        try {
             payment = checkoutPayment(
                    savedOrder,
                    paymentMethod,
                    request.getPaidAmount(),
                    request.getChangedBy()
            );
        }catch (RuntimeException e) {
            throw new RuntimeException(e);
        }


        Order paidOrder = orderRepository.save(savedOrder);
        return toResponse(paidOrder, payment);
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
        return orderMapper.toPaymentInfoResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        Order order = getExistingOrder(orderId);
        BigDecimal paidAmount = orderAmountCalculator.defaultZero(request.getPaidAmount());
        orderValidator.validateMarkPaid(order, paidAmount);

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
                "Thanh toán thành công, mã thanh toán: " + request.getPaymentCode(),
                request.getChangedBy()
        );

        UUID sagaId = orderPaymentSagaService.completeManualPaymentSaga(savedOrder, request.getPaymentCode());
        orderCustomerServiceImpl.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(savedOrder, request.getPaymentCode(), sagaId.toString());

        return toResponse(savedOrder);
    }

    @Override
    @Transactional
    public void handlePaymentEvent(PaymentOrderEventPayload event) {
        if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
            return;
        }

        String eventType = normalizePaymentEventType(event.getEventType());
        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case "PAYMENT_SUCCEEDED" -> handlePaymentSucceededEvent(event);
            case "PAYMENT_FAILED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.PAYMENT_FAILED,
                    "Thanh toán thất bại"
            );
            case "PAYMENT_EXPIRED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.EXPIRED,
                    "Thanh toán hết hạn"
            );
            case "PAYMENT_CANCELLED" -> handlePaymentFailedEvent(
                    event,
                    OrderStatus.CANCELLED,
                    "Thanh toán đã bị hủy"
            );
            case "PAYMENT_NEED_REVIEW" -> handlePaymentNeedReviewEvent(event);
            case "PAYMENT_WAITING", "PAYMENT_WAITING_PAYMENT" -> handlePaymentWaitingEvent(event);
            default -> log.debug("Bỏ qua payment event chưa được Order-Service xử lý: {}", eventType);
        }
    }

    @Override
    @Transactional
    public void handlePaymentSucceeded(PaymentOrderEventPayload event) {
        handlePaymentEvent(event);
    }

    private void handlePaymentSucceededEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            orderPaymentSagaService.completePaymentSaga(order, event, paymentCausationId(event));
            return;
        }

        BigDecimal amountPaid = orderAmountCalculator.defaultZero(event.getAmountPaid());
        orderValidator.validatePaymentAmount(order, amountPaid);

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentMethod(firstNonBlank(normalizePaymentMethod(event.getPaymentMethod()), order.getPaymentMethod()));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);
        saveStatusHistory(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                "Thanh toán chuyển khoản thành công, mã thanh toán: " + savedOrder.getPaymentCode(),
                null
        );

        UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, paymentCausationId(event));
        orderCustomerServiceImpl.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(savedOrder, paymentCausationId(event), sagaId.toString());
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder,
                event,
                oldStatus,
                OrderStatus.COMPLETED,
                sagaId.toString()
        );
    }

    private void handlePaymentFailedEvent(PaymentOrderEventPayload event, OrderStatus targetOrderStatus, String defaultReason) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(targetOrderStatus);
        order.setPaymentMethod(firstNonBlank(normalizePaymentMethod(event.getPaymentMethod()), order.getPaymentMethod()));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        String reason = firstNonBlank(event.getFailureReason(), defaultReason);
        saveStatusHistory(savedOrder, oldStatus, targetOrderStatus, reason, null);
        orderPaymentSagaService.failPaymentSaga(savedOrder, event, reason);

        if (targetOrderStatus == OrderStatus.CANCELLED) {
            orderOutboxPublisher.publishOrderCancelledNotification(
                    savedOrder,
                    event,
                    oldStatus,
                    targetOrderStatus,
                    event.getSagaId(),
                    reason
            );
        } else {
            orderOutboxPublisher.publishOrderPaymentFailedNotification(
                    savedOrder,
                    event,
                    oldStatus,
                    targetOrderStatus,
                    event.getSagaId(),
                    reason
            );
        }
    }

    private void handlePaymentNeedReviewEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentMethod(firstNonBlank(normalizePaymentMethod(event.getPaymentMethod()), order.getPaymentMethod()));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        String reason = firstNonBlank(event.getFailureReason(), "Thanh toán cần kiểm tra thủ công");
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            saveStatusHistory(savedOrder, oldStatus, OrderStatus.PENDING_PAYMENT, reason, null);
        }
        orderPaymentSagaService.markPaymentNeedReview(savedOrder, event, reason);
    }

    private void handlePaymentWaitingEvent(PaymentOrderEventPayload event) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentMethod(firstNonBlank(normalizePaymentMethod(event.getPaymentMethod()), order.getPaymentMethod()));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            saveStatusHistory(savedOrder, oldStatus, OrderStatus.PENDING_PAYMENT, "Đang chờ thanh toán", null);
        }
        orderPaymentSagaService.markPaymentWaiting(savedOrder, event, "Đang chờ payment event thành công");
    }

    @Override
    @Transactional
    public OrderResponse addDraftItem(AddDraftOrderItemRequest request) {
        Instant expiresAt = orderDateTimeHelper.nextDraftExpiresAt();
        Order order = request.getOrderId() == null
                ? createDraftOrder(request, expiresAt)
                : getEditableDraftOrder(request.getOrderId());
        order.setExpiresAt(expiresAt);

        var variant = catalogClient.getProductVariantPosView(request.getProductVariantId());
        log.info("variant: {}", variant);
        BigDecimal addQuantity = orderValidator.requirePositiveQuantity(request.getQuantity());
        OrderItem item = findDraftItem(order, variant.variantId(), request.getBomVersionId());
        BigDecimal oldQuantity = item == null
                ? BigDecimal.ZERO
                : orderAmountCalculator.defaultZero(item.getQuantity());
        BigDecimal targetQuantity = oldQuantity.add(addQuantity);
        log.info("varient{} {}", variant.variantId(), request.getProductVariantId());
        inventoryReservationService.applyReservation(order, variant.variantId(), targetQuantity, request.getInventoryLocationId());

        if (item == null) {
            item = orderItemMapper.toDraftOrderItem(
                    variant,
                    targetQuantity,
                    orderAmountCalculator.defaultZero(variant.sellingPrice()),
                    orderAmountCalculator.defaultZero(request.getDiscountAmount()),
                    request.getBomVersionId()
            );
            orderAmountCalculator.recalculateLine(item);
            order.addItem(item);
        } else {
            item.setQuantity(targetQuantity);
            orderAmountCalculator.recalculateLine(item);
        }

        orderAmountCalculator.recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse updateDraftItemQuantity(UUID orderId, UUID itemId, UpdateDraftOrderItemQuantityRequest request) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);
        BigDecimal targetQuantity = orderAmountCalculator.defaultZero(request.getQuantity());
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());

        if (targetQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return removeDraftItem(orderId, itemId);
        }

        inventoryReservationService.applyReservation(order, item.getProductVariantId(), targetQuantity, null);
        item.setQuantity(targetQuantity);
        orderAmountCalculator.recalculateLine(item);
        orderAmountCalculator.recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse removeDraftItem(UUID orderId, UUID itemId) {
        Order order = getEditableDraftOrder(orderId);
        OrderItem item = getOrderItem(order, itemId);

        inventoryReservationService.releaseItemReservation(order.getId(), item.getProductVariantId(), null);
        order.getItems().remove(item);
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());
        orderAmountCalculator.recalculateOrderTotals(order);
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional
    public OrderResponse cancelDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        if (OrderStatus.CANCELLED == order.getStatus() || OrderStatus.EXPIRED == order.getStatus()) {
            return toResponse(order);
        }
        orderValidator.validateDraftCancellation(order);

        inventoryReservationService.releaseOrderReservation(order.getId());
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
        orderValidator.validateDraftHasItems(order);

        if (request.getDiscountAmount() != null) {
            order.setDiscountAmount(
                    orderAmountCalculator.defaultZero(request.getDiscountAmount()).max(BigDecimal.ZERO)
            );
        }
        orderAmountCalculator.recalculateOrderTotals(order);
        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            throw new InvalidPaymentAmountException("Phương thức thanh toán không hợp lệ");
        }

        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(
                request.getPaymentCode(),
                orderCodeGenerator.generatePosPaymentCode(order, paymentMethod)
        ));
        Order savedOrder = orderRepository.save(order);

        PaymentResponse payment = checkoutPayment(
                savedOrder,
                paymentMethod,
                request.getPaidAmount(),
                request.getChangedBy()
        );

        Order checkoutOrder = orderRepository.save(savedOrder);
        return toResponse(checkoutOrder, payment);
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
        ).map(orderMapper::toSummaryResponse);
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
        LocalDate today = orderDateTimeHelper.today();
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
        LocalDate today = orderDateTimeHelper.today();
        List<Order> todayOrders = orderRepository.findAll(
                orderSpecification(null, null, null, null, today, today)
        );
        return orderAmountCalculator.calculateTodaySalesSummary(todayOrders);
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
        orderValidator.validateCancellation(order);

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
        Order order = orderMapper.toDraftOrder(
                request,
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                expiresAt
        );
        Order savedOrder = orderRepository.saveAndFlush(order);
        saveStatusHistory(savedOrder, null, OrderStatus.DRAFT, "Tạo hóa đơn nháp POS", request.getCashierId());
        return savedOrder;
    }

    private Order getEditableDraftOrder(UUID orderId) {
        Order order = getExistingOrder(orderId);
        orderValidator.validateEditableDraft(order, Instant.now());
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
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(fromDate)
                ));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(toDate.plusDays(1))
                ));
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
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(fromDate)
                ));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(toDate.plusDays(1))
                ));
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
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(fromDate)
                ));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"),
                        orderDateTimeHelper.startOfDay(toDate.plusDays(1))
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        });
    }

    private OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    private OrderResponse toResponse(Order order, PaymentResponse payment) {
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order.getId());
        BigDecimal changeAmount = paymentInfo == null
                ? BigDecimal.ZERO
                : orderAmountCalculator.defaultZero(paymentInfo.changeAmount());
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);
        OrderResponse response = orderMapper.toResponse(
                order,
                payment,
                changeAmount,
                paymentMethod
        );
        response.setStatusHistories(toStatusHistoryResponses(order.getId()));
        return response;
    }

    private TodaySalesItemResponse toTodaySalesItemResponse(Order order) {
        PaymentClient.PaymentOrderInfoResponse paymentInfo = fetchPaymentInfo(order.getId());
        String paymentMethod = resolvePaymentMethod(order, paymentInfo);
        return orderMapper.toTodaySalesItemResponse(order, paymentMethod);
    }

    private List<OrderStatusHistoryResponse> toStatusHistoryResponses(UUID orderId) {
        return orderStatusHistoryRepository.findByOrderIdAndIsDeletedFalseOrderByChangedAtAsc(orderId)
                .stream()
                .map(orderMapper::toStatusHistoryResponse)
                .toList();
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

    private boolean paymentMethodMatches(TodaySalesItemResponse item, String paymentMethod) {
        String normalizedPaymentMethod = normalizePaymentMethod(paymentMethod);
        return normalizedPaymentMethod == null
                || normalizedPaymentMethod.equals(normalizePaymentMethod(item.getPaymentMethod()));
    }

    private PaymentResponse checkoutPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount,
            UUID changedBy
    ) {
        PaymentMethod method = toSupportedPaymentMethod(paymentMethod);
        orderValidator.validateCashPaymentAmount(order, method, receivedAmount);

        UUID sagaId = orderPaymentSagaService.startPaymentSaga(order, method.name());

        PaymentCreateRequest paymentRequest = new PaymentCreateRequest();
        paymentRequest.setOrderId(order.getId());
        paymentRequest.setOrderCode(order.getOrderCode());
        paymentRequest.setCustomerId(order.getCustomerId());
        paymentRequest.setSagaId(sagaId);
        paymentRequest.setPaymentMethod(method);
        paymentRequest.setGoodsAmount(order.getOriginalAmount());
        paymentRequest.setDiscountAmount(order.getDiscountAmount());
        paymentRequest.setDeliveryFee(order.getShippingFee());
        paymentRequest.setFinalAmount(order.getFinalAmount());
        paymentRequest.setReceivedAmount(method == PaymentMethod.CASH ? receivedAmount : null);
        paymentRequest.setDeliveryRequired(false);

        PaymentResponse payment = paymentClient.checkout(paymentRequest);
        applyPaymentCheckoutResult(order, payment, changedBy, sagaId);
        return payment;
    }

    private void applyPaymentCheckoutResult(Order order, PaymentResponse payment, UUID changedBy, UUID sagaId) {
        orderValidator.validatePaymentCheckoutResult(payment);

        OrderStatus oldStatus = order.getStatus();
        String paymentStatus = payment.getStatus().trim().toUpperCase(Locale.ROOT);
        String paymentMethod = normalizePaymentMethod(payment.getMethod());

        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(payment.getPaymentCode(), order.getPaymentCode()));

        if ("PAID".equals(paymentStatus)) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(OrderStatus.COMPLETED);
            order.setPaidAmount(order.getFinalAmount());
            order.setDebtAmount(BigDecimal.ZERO);

            saveStatusHistory(
                    order,
                    oldStatus,
                    OrderStatus.COMPLETED,
                    "Thanh toán POS trực tiếp bằng " + paymentMethod + ", mã thanh toán: " + order.getPaymentCode(),
                    changedBy
            );
            orderPaymentSagaService.completePaymentSaga(order, eventFromPaymentResponse(order, payment, sagaId), order.getPaymentCode());
            orderCustomerServiceImpl.updateCustomerPurchase(order.getId());
            orderOutboxPublisher.publishPaymentCompletedOrchestration(order, order.getPaymentCode(), sagaId.toString());
            return;
        }

        if ("WAITING_PAYMENT".equals(paymentStatus) || "PENDING".equals(paymentStatus)) {
            order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
            order.setStatus(OrderStatus.PENDING_PAYMENT);
            order.setPaidAmount(BigDecimal.ZERO);
            order.setDebtAmount(order.getFinalAmount());

            saveStatusHistory(
                    order,
                    oldStatus,
                    OrderStatus.PENDING_PAYMENT,
                    "Tạo yêu cầu thanh toán " + paymentMethod + ", mã thanh toán: " + order.getPaymentCode(),
                    changedBy
            );
            orderPaymentSagaService.markPaymentRequested(order, sagaId, payment);
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        String error = "Payment-Service trả về trạng thái không thành công: " + paymentStatus;
        saveStatusHistory(
                order,
                oldStatus,
                OrderStatus.PAYMENT_FAILED,
                error,
                changedBy
        );
        orderPaymentSagaService.failPaymentSaga(order, eventFromPaymentResponse(order, payment, sagaId), error);
    }


    private PaymentOrderEventPayload eventFromPaymentResponse(Order order, PaymentResponse payment, UUID sagaId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setSagaId(sagaId == null ? null : sagaId.toString());
        event.setOrderId(order.getId().toString());
        event.setOrderCode(order.getOrderCode());
        event.setPaymentId(payment.getPaymentId() == null ? null : payment.getPaymentId().toString());
        event.setPaymentCode(firstNonBlank(payment.getPaymentCode(), order.getPaymentCode()));
        event.setPaymentMethod(payment.getMethod());
        event.setPaymentProvider(payment.getProvider());
        event.setPaymentStatus(payment.getStatus());
        event.setAmountRequired(payment.getAmountRequired());
        event.setAmountPaid(payment.getAmountPaid());
        event.setEventType("PAID".equalsIgnoreCase(payment.getStatus()) ? "PAYMENT_SUCCEEDED" : "PAYMENT_FAILED");
        event.setEventKey(order.getId().toString());
        return event;
    }

    private String paymentCausationId(PaymentOrderEventPayload event) {
        if (event == null) {
            return null;
        }
        return firstNonBlank(
                event.getEventId(),
                firstNonBlank(event.getEventKey(), firstNonBlank(event.getPaymentCode(), event.getPaymentId()))
        );
    }

    private String normalizePaymentEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }

        String normalized = eventType.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace('.', '_');

        if (normalized.endsWith("PAYMENT_SUCCEEDED") || normalized.endsWith("SUCCEEDED")) {
            return "PAYMENT_SUCCEEDED";
        }
        if (normalized.endsWith("PAYMENT_FAILED") || normalized.endsWith("FAILED")) {
            return "PAYMENT_FAILED";
        }
        if (normalized.endsWith("PAYMENT_EXPIRED") || normalized.endsWith("EXPIRED")) {
            return "PAYMENT_EXPIRED";
        }
        if (normalized.endsWith("PAYMENT_CANCELLED") || normalized.endsWith("PAYMENT_CANCELED")
                || normalized.endsWith("CANCELLED") || normalized.endsWith("CANCELED")) {
            return "PAYMENT_CANCELLED";
        }
        if (normalized.endsWith("PAYMENT_NEED_REVIEW") || normalized.endsWith("NEED_REVIEW")) {
            return "PAYMENT_NEED_REVIEW";
        }
        if (normalized.endsWith("PAYMENT_WAITING_PAYMENT") || normalized.endsWith("WAITING_PAYMENT")
                || normalized.endsWith("PAYMENT_WAITING") || normalized.endsWith("WAITING")) {
            return "PAYMENT_WAITING";
        }

        return normalized;
    }

    private PaymentMethod toSupportedPaymentMethod(String paymentMethod) {
        String normalized = normalizePaymentMethod(paymentMethod);
        if (normalized == null) {
            throw new InvalidPaymentAmountException("Phương thức thanh toán không hợp lệ");
        }
        return switch (normalized) {
            case "CASH" -> PaymentMethod.CASH;
            case "BANK_TRANSFER" -> PaymentMethod.BANK_TRANSFER;
            default -> throw new InvalidPaymentAmountException(
                    "Payment-Service chưa hỗ trợ phương thức thanh toán: " + normalized
            );
        };
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
        if (order.getPaymentMethod() != null && !order.getPaymentMethod().isBlank()) {
            return normalizePaymentMethod(order.getPaymentMethod());
        }
        if (paymentInfo != null && paymentInfo.method() != null) {
            return normalizePaymentMethod(paymentInfo.method());
        }
        if (orderAmountCalculator.defaultZero(order.getDebtAmount()).compareTo(BigDecimal.ZERO) > 0
                || orderAmountCalculator.isPendingPaymentSale(order)) {
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
            case "BANK", "TRANSFER", "BANK_TRANSFER", "QR_CODE" -> "BANK_TRANSFER";
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

}
