package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.utils.SecurityUtils;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.*;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.*;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
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
import org.hvtra.orderservice.exception.OrderAlreadyPaidException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderInventoryReservationService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
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

import static org.hvtra.orderservice.domain.enums.DiscountType.*;

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
    private final PaymentClient paymentClient;
    private final OrderCustomerServiceImpl orderCustomerServiceImpl;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderInventoryReservationService inventoryReservationService;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderDiscountRepository orderDiscountRepository;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (request.getOrderId() != null) {
            return checkoutExistingDraftOrder(request);
        }

        CustomerResponse customerSnapshot =
                loadCustomerSnapshot(request.getCustomerId());

        BigDecimal shippingFee = defaultZero(
                request.getShippingFee()
        ).setScale(2, RoundingMode.HALF_UP);

        if (shippingFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Phí giao hàng không được âm"
            );
        }

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .tenantId(request.getTenantId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .customerNameSnapshot(firstNonBlank(
                        customerSnapshot == null
                                ? null
                                : customerSnapshot.getFullName(),
                        request.getCustomerNameSnapshot()
                ))
                .customerPhoneSnapshot(firstNonBlank(
                        customerSnapshot == null
                                ? null
                                : customerSnapshot.getPhone(),
                        request.getCustomerPhoneSnapshot()
                ))
                .customerEmailSnapshot(firstNonBlank(
                        customerSnapshot == null
                                ? null
                                : customerSnapshot.getEmail(),
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
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(shippingFee)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .build();

        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal totalItemDiscount = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            BigDecimal quantity = defaultZero(
                    itemRequest.getQuantity()
            );

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Số lượng sản phẩm phải lớn hơn 0"
                );
            }

            var variant = catalogClient.getProductVariantPosView(
                    itemRequest.getProductVariantId()
            );

            if (variant == null || variant.sellingPrice() == null) {
                throw new IllegalArgumentException(
                        "Không tìm thấy giá bán của sản phẩm: "
                                + itemRequest.getProductVariantId()
                );
            }

            BigDecimal actualUnitPrice = variant.sellingPrice()
                    .setScale(2, RoundingMode.HALF_UP);

            if (actualUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Giá bán sản phẩm không hợp lệ: "
                                + itemRequest.getProductVariantId()
                );
            }

            BigDecimal grossLineAmount = actualUnitPrice
                    .multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal itemDiscount = calculateDiscounts(
                    grossLineAmount,
                    itemRequest.getItemDiscounts()
            );

            BigDecimal lineTotal =
                    grossLineAmount.subtract(itemDiscount);

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

            originalAmount = originalAmount.add(grossLineAmount);
            totalItemDiscount = totalItemDiscount.add(itemDiscount);
        }

        BigDecimal amountAfterItemDiscount =
                originalAmount.subtract(totalItemDiscount);

        BigDecimal orderDiscountAmount = calculateDiscounts(
                amountAfterItemDiscount,
                request.getOrderDiscounts()
        );

        BigDecimal totalDiscountAmount =
                totalItemDiscount.add(orderDiscountAmount);

        BigDecimal finalAmount = originalAmount
                .subtract(totalDiscountAmount)
                .add(shippingFee)
                .setScale(2, RoundingMode.HALF_UP);

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(totalDiscountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(finalAmount);

        Order savedOrder = orderRepository.saveAndFlush(order);

        saveDiscountDetails(savedOrder, request);

        saveStatusHistory(
                savedOrder,
                null,
                savedOrder.getStatus(),
                "Tạo đơn hàng",
                request.getCashierId()
        );

        String paymentMethod =
                normalizePaymentMethod(request.getPaymentMethod());

        if (paymentMethod == null) {
            return toResponse(savedOrder);
        }

        PaymentResponse payment = checkoutPayment(
                savedOrder,
                paymentMethod,
                request.getPaidAmount()
        );

        return toResponse(savedOrder, payment);
    }

    private OrderResponse checkoutExistingDraftOrder(CreateOrderRequest request) {
        Order order = getEditableDraftOrder(request.getOrderId());
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidOrderStatusException("Chưa có sản phẩm trong đơn");
        }

        updateDraftOrderCheckoutInfo(order, request);
        applyDraftCheckoutDiscounts(order, request);

        String paymentMethod =
                normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            throw new InvalidPaymentAmountException(
                    "Phương thức thanh toán không hợp lệ"
            );
        }

        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.WAITING_PAYMENT);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(
                request.getPaymentCode(),
                generatePosPaymentCode(order, paymentMethod)
        ));
        order.setExpiresAt(null);

        Order savedOrder = orderRepository.saveAndFlush(order);

        saveStatusHistory(
                savedOrder,
                OrderStatus.DRAFT,
                OrderStatus.PENDING_PAYMENT,
                "Checkout hóa đơn nháp POS",
                request.getCashierId()
        );

        PaymentResponse payment = checkoutPayment(
                savedOrder,
                paymentMethod,
                request.getPaidAmount()
        );

        return toResponse(savedOrder, payment);
    }

    private void updateDraftOrderCheckoutInfo(
            Order order,
            CreateOrderRequest request
    ) {
        CustomerResponse customerSnapshot =
                loadCustomerSnapshot(request.getCustomerId());

        BigDecimal shippingFee = defaultZero(
                request.getShippingFee()
        ).setScale(2, RoundingMode.HALF_UP);

        if (shippingFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Phí giao hàng không được âm"
            );
        }

        order.setTenantId(firstNonNull(request.getTenantId(), order.getTenantId()));
        order.setTerminalId(firstNonNull(request.getTerminalId(), order.getTerminalId()));
        order.setShiftId(firstNonNull(request.getShiftId(), order.getShiftId()));
        order.setCustomerId(request.getCustomerId());
        order.setCustomerNameSnapshot(firstNonBlank(
                customerSnapshot == null ? null : customerSnapshot.getFullName(),
                request.getCustomerNameSnapshot()
        ));
        order.setCustomerPhoneSnapshot(firstNonBlank(
                customerSnapshot == null ? null : customerSnapshot.getPhone(),
                request.getCustomerPhoneSnapshot()
        ));
        order.setCustomerEmailSnapshot(firstNonBlank(
                customerSnapshot == null ? null : customerSnapshot.getEmail(),
                request.getCustomerEmailSnapshot()
        ));
        order.setBranchNameSnapshot(request.getBranchNameSnapshot());
        order.setTerminalNameSnapshot(request.getTerminalNameSnapshot());
        order.setShiftCodeSnapshot(request.getShiftCodeSnapshot());
        order.setCashierId(firstNonNull(request.getCashierId(), order.getCashierId()));
        order.setCashierNameSnapshot(firstNonBlank(
                request.getCashierNameSnapshot(),
                order.getCashierNameSnapshot()
        ));
        order.setRecipientNameSnapshot(request.getRecipientNameSnapshot());
        order.setRecipientPhoneSnapshot(request.getRecipientPhoneSnapshot());
        order.setShippingAddressSnapshot(request.getShippingAddressSnapshot());
        order.setShippingProviderSnapshot(request.getShippingProviderSnapshot());
        order.setOrderType(normalizeOrderType(request.getOrderType()));
        order.setShippingFee(shippingFee);
    }

    private void applyDraftCheckoutDiscounts(
            Order order,
            CreateOrderRequest request
    ) {
        clearDiscountDetails(order);

        List<OrderDiscount> discountEntities = new ArrayList<>();
        List<UUID> processedItemIds = new ArrayList<>();
        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal totalItemDiscount = BigDecimal.ZERO;

        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = findDraftItem(
                    order,
                    itemRequest.getProductVariantId(),
                    itemRequest.getBomVersionId()
            );

            if (item == null) {
                throw new InvalidOrderStatusException(
                        "Sản phẩm checkout không có trong hóa đơn nháp: "
                                + itemRequest.getProductVariantId()
                );
            }

            if (processedItemIds.contains(item.getId())) {
                throw new InvalidOrderStatusException(
                        "Sản phẩm checkout bị trùng trong request: "
                                + itemRequest.getProductVariantId()
                );
            }
            processedItemIds.add(item.getId());

            BigDecimal requestedQuantity =
                    defaultPositive(itemRequest.getQuantity());
            if (requestedQuantity.compareTo(defaultZero(item.getQuantity())) != 0) {
                throw new InvalidOrderStatusException(
                        "Số lượng checkout không khớp hóa đơn nháp: "
                                + itemRequest.getProductVariantId()
                );
            }

            BigDecimal grossLineAmount = defaultZero(item.getActualUnitPrice())
                    .multiply(defaultZero(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal itemDiscount = calculateDiscounts(
                    grossLineAmount,
                    itemRequest.getItemDiscounts()
            );

            item.setDiscountAmount(itemDiscount);
            item.setLineTotal(grossLineAmount.subtract(itemDiscount));

            discountEntities.addAll(createDiscountEntities(
                    order,
                    item,
                    DiscountScope.ITEM,
                    grossLineAmount,
                    itemRequest.getItemDiscounts()
            ));

            originalAmount = originalAmount.add(grossLineAmount);
            totalItemDiscount = totalItemDiscount.add(itemDiscount);
        }

        if (processedItemIds.size() != order.getItems().size()) {
            throw new InvalidOrderStatusException(
                    "Danh sách sản phẩm checkout không khớp hóa đơn nháp"
            );
        }

        BigDecimal orderDiscountBase =
                originalAmount.subtract(totalItemDiscount);
        BigDecimal orderDiscountAmount = calculateDiscounts(
                orderDiscountBase,
                request.getOrderDiscounts()
        );

        discountEntities.addAll(createDiscountEntities(
                order,
                null,
                DiscountScope.ORDER,
                orderDiscountBase,
                request.getOrderDiscounts()
        ));

        BigDecimal totalDiscountAmount =
                totalItemDiscount.add(orderDiscountAmount);
        BigDecimal finalAmount = originalAmount
                .subtract(totalDiscountAmount)
                .add(defaultZero(order.getShippingFee()))
                .setScale(2, RoundingMode.HALF_UP);

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(totalDiscountAmount);
        order.setFinalAmount(finalAmount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(finalAmount);

        if (!discountEntities.isEmpty()) {
            List<OrderDiscount> savedDiscounts =
                    orderDiscountRepository.saveAll(discountEntities);
            order.getDiscounts().addAll(savedDiscounts);
        }
    }

    private void clearDiscountDetails(Order order) {
        if (order.getDiscounts() == null) {
            order.setDiscounts(new ArrayList<>());
            return;
        }

        if (!order.getDiscounts().isEmpty()) {
            orderDiscountRepository.deleteAll(order.getDiscounts());
            order.getDiscounts().clear();
        }
    }

    private void saveDiscountDetails(
            Order order,
            CreateOrderRequest request
    ) {
        List<OrderDiscount> entities = new ArrayList<>();

        for (int index = 0;
             index < request.getItems().size();
             index++) {

            CreateOrderItemRequest itemRequest =
                    request.getItems().get(index);

            OrderItem item = order.getItems().get(index);

            BigDecimal grossLineAmount = item
                    .getActualUnitPrice()
                    .multiply(item.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);

            entities.addAll(createDiscountEntities(
                    order,
                    item,
                    DiscountScope.ITEM,
                    grossLineAmount,
                    itemRequest.getItemDiscounts()
            ));
        }

        BigDecimal totalItemDiscount = order.getItems().stream()
                .map(OrderItem::getDiscountAmount)
                .map(this::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal orderDiscountBase = order
                .getOriginalAmount()
                .subtract(totalItemDiscount);

        entities.addAll(createDiscountEntities(
                order,
                null,
                DiscountScope.ORDER,
                orderDiscountBase,
                request.getOrderDiscounts()
        ));

        if (!entities.isEmpty()) {
            List<OrderDiscount> savedDiscounts =
                    orderDiscountRepository.saveAll(entities);
            if (order.getDiscounts() == null) {
                order.setDiscounts(new ArrayList<>());
            }
            order.getDiscounts().clear();
            order.getDiscounts().addAll(savedDiscounts);
        }
    }

    private List<OrderDiscount> createDiscountEntities(
            Order order,
            OrderItem orderItem,
            DiscountScope scope,
            BigDecimal startingAmount,
            List<CreateDiscountRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<OrderDiscount> entities = new ArrayList<>();
        BigDecimal remainingAmount = startingAmount;

        for (int index = 0; index < requests.size(); index++) {
            CreateDiscountRequest request = requests.get(index);

            BigDecimal appliedAmount =
                    calculateDiscountAmount(
                            remainingAmount,
                            request
                    );

            OrderDiscount entity = new OrderDiscount();
            entity.setOrder(order);
            entity.setOrderItem(orderItem);
            entity.setScope(scope);
            entity.setSource(request.getSource());
            entity.setDiscountType(request.getDiscountType());
            entity.setUsageId(request.getUsageId());
            entity.setDiscountValue(request.getDiscountValue());
            entity.setBaseAmount(remainingAmount);
            entity.setAppliedAmount(appliedAmount);
            entity.setReasonCode(request.getReasonCode());
            entity.setNote(request.getNote());
            entity.setStatus(DiscountStatus.ACTIVE);
            entity.setAppliedBy(order.getCashierId());
            entity.setAppliedByNameSnapshot(
                    order.getCashierNameSnapshot()
            );

            entities.add(entity);
            remainingAmount =
                    remainingAmount.subtract(appliedAmount);
        }

        return entities;
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
        orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
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

        BigDecimal amountPaid = defaultZero(event.getAmountPaid());
        if (amountPaid.compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }

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
        orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
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
        inventoryReservationService.applyReservation(order, variant.variantId(), targetQuantity, request.getInventoryLocationId());

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

        inventoryReservationService.applyReservation(order, item.getProductVariantId(), targetQuantity, null);
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

        inventoryReservationService.releaseItemReservation(order.getId(), item.getProductVariantId(), null);
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
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidOrderStatusException("Chưa có sản phẩm trong đơn");
        }

        if (request.getDiscountAmount() != null) {
            order.setDiscountAmount(
                    defaultZero(request.getDiscountAmount()).max(BigDecimal.ZERO)
            );
        }
        recalculateOrderTotals(order);
        String paymentMethod = normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            throw new InvalidPaymentAmountException("Phương thức thanh toán không hợp lệ");
        }

        order.setPaymentMethod(paymentMethod);
        order.setPaymentCode(firstNonBlank(request.getPaymentCode(), generatePosPaymentCode(order, paymentMethod)));
        Order savedOrder = orderRepository.save(order);

        PaymentResponse payment = checkoutPayment(
                savedOrder,
                paymentMethod,
                request.getPaidAmount()
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

        return orderRepository.findAll(
                orderSpecification(
                        keyword,
                        status,
                        paymentStatus,
                        orderType,
                        fromDate,
                        toDate,
                        sort == null || sort.isBlank() ? SORT_BUSINESS_STATUS : sort
                ),
                pageRequest
        ).map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats(LocalDate fromDate, LocalDate toDate) {
        Specification<Order> dateRange = orderSpecification(null, null, null, null, fromDate, toDate, null);

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

        Page<Order> orderPage = orderRepository.findAll(
                orderSpecification(
                        null,
                        status,
                        paymentStatus,
                        paymentMethod,
                        null,
                        today,
                        today,
                        null
                ),
                pageable
        );

        return orderPage.map(this::toTodaySalesItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TodaySalesSummaryResponse getTodaySalesSummary() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<Order> todayOrders = orderRepository.findAll(
                orderSpecification(null, null, null, null, today, today, null)
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
            LocalDate toDate,
            String sort
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            applyDefaultOrder(root, query, criteriaBuilder, sort);

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

    private Specification<Order> orderSpecification(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethod,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate,
            String sort
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.isFalse(root.get("isDeleted")));
            applyDefaultOrder(root, query, criteriaBuilder, sort);

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("orderCode")),
                                pattern
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("customerNameSnapshot")),
                                pattern
                        ),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("customerPhoneSnapshot")),
                                pattern
                        )
                ));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("status"),
                        status
                ));
            }

            if (paymentStatus != null) {
                if (paymentStatus == PaymentStatus.UNPAID) {
                    predicates.add(root.get("paymentStatus").in(
                            PaymentStatus.UNPAID,
                            PaymentStatus.WAITING_PAYMENT
                    ));
                } else {
                    predicates.add(criteriaBuilder.equal(
                            root.get("paymentStatus"),
                            paymentStatus
                    ));
                }
            }

            if (paymentMethod != null && !paymentMethod.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("paymentMethod")),
                        paymentMethod.trim().toUpperCase(Locale.ROOT)
                ));
            }

            if (orderType != null && !orderType.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        root.get("orderType"),
                        normalizeOrderType(orderType)
                ));
            }

            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        startOfDay(fromDate)
                ));
            }

            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(
                        root.get("createdAt"),
                        startOfDay(toDate.plusDays(1))
                ));
            }

            return criteriaBuilder.and(
                    predicates.toArray(Predicate[]::new)
            );
        };
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
        return criteriaBuilder.<OrderStatus, Integer>selectCase(root.get("status"))
                .when(OrderStatus.PENDING_PAYMENT, 1)
                .when(OrderStatus.PAYMENT_CONFIRMED, 2)
                .when(OrderStatus.COMPLETED, 3)
                .when(OrderStatus.PAYMENT_FAILED, 4)
                .when(OrderStatus.EXPIRED, 5)
                .when(OrderStatus.CANCELLED, 6)
                .when(OrderStatus.DRAFT, 7)
                .otherwise(99);
    }

    private Expression<Integer> invoicePaymentPriority(Root<Order> root, CriteriaBuilder criteriaBuilder) {
        return criteriaBuilder.<PaymentStatus, Integer>selectCase(root.get("paymentStatus"))
                .when(PaymentStatus.UNPAID, 1)
                .when(PaymentStatus.WAITING_PAYMENT, 1)
                .when(PaymentStatus.PAID, 2)
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
        return toResponse(order, null);
    }

    private OrderResponse toResponse(Order order, PaymentResponse payment) {
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
                .payment(payment)
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

    private BigDecimal calculateDiscounts(
            BigDecimal baseAmount,
            List<CreateDiscountRequest> discounts
    ) {
        if (discounts == null || discounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        if (baseAmount == null
                || baseAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Số tiền làm cơ sở giảm giá không hợp lệ"
            );
        }

        BigDecimal remainingAmount = baseAmount;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (CreateDiscountRequest discount : discounts) {
            BigDecimal appliedAmount = calculateDiscountAmount(
                    remainingAmount,
                    discount
            );

            totalDiscount = totalDiscount.add(appliedAmount);
            remainingAmount = remainingAmount.subtract(appliedAmount);
        }

        return totalDiscount.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateDiscountAmount(
            BigDecimal baseAmount,
            CreateDiscountRequest discount
    ) {
        if (discount == null
                || discount.getDiscountType() == null
                || discount.getDiscountValue() == null) {
            throw new IllegalArgumentException(
                    "Thông tin giảm giá không hợp lệ"
            );
        }

        BigDecimal discountValue =
                discount.getDiscountValue();

        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Giá trị giảm giá phải lớn hơn 0"
            );
        }

        BigDecimal appliedAmount;

        if (discount.getDiscountType()
                == DiscountType.PERCENTAGE) {

            if (discountValue.compareTo(
                    BigDecimal.valueOf(100)
            ) > 0) {
                throw new IllegalArgumentException(
                        "Phần trăm giảm giá không được vượt quá 100"
                );
            }

            appliedAmount = baseAmount
                    .multiply(discountValue)
                    .divide(
                            BigDecimal.valueOf(100),
                            2,
                            RoundingMode.HALF_UP
                    );

        } else if (discount.getDiscountType()
                == DiscountType.FIXED_AMOUNT) {

            appliedAmount = discountValue.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

        } else {
            throw new IllegalArgumentException(
                    "Loại giảm giá không được hỗ trợ: "
                            + discount.getDiscountType()
            );
        }

        if (appliedAmount.compareTo(baseAmount) > 0) {
            throw new IllegalArgumentException(
                    "Số tiền giảm vượt quá giá trị còn lại"
            );
        }

        return appliedAmount;
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

    private PaymentResponse checkoutPayment(
            Order order,
            String paymentMethod,
            BigDecimal receivedAmount
    ) {
        PaymentMethod method = toSupportedPaymentMethod(paymentMethod);
        if (method == PaymentMethod.CASH
                && defaultZero(receivedAmount).compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }

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
        applyPaymentCheckoutResult(order, payment, getUserId(), sagaId);
        return payment;
    }

    private void applyPaymentCheckoutResult(Order order, PaymentResponse payment, UUID changedBy, UUID sagaId) {
        if (payment == null || payment.getStatus() == null) {
            throw new InvalidPaymentAmountException("Payment-Service không trả về kết quả thanh toán");
        }

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
            orderPaymentSagaService.markResourceConfirmationRequested(order, sagaId);
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
            case "BANK", "TRANSFER", "BANK_TRANSFER", "QR_CODE" -> "BANK_TRANSFER";
            case "CASH" -> "CASH";
            case "DEBT", "CREDIT" -> "DEBT";
            case "CARD" -> "CARD";
            case "E_WALLET" -> "E_WALLET";
            case "COMBINED" -> "COMBINED";
            default -> normalized;
        };
    }

    private String generatePosPaymentCode(Order order, String paymentMethod) {
        return "POS-%s-%s".formatted(
                paymentMethod,
                order.getOrderCode() != null ? order.getOrderCode() : order.getId());
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

    private <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
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

    private UUID getUserId() {
        return UUID.fromString(SecurityUtils.currentUserId());
    }
}
