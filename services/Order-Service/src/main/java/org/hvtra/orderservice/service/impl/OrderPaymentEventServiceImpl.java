package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.support.OrderBackorderSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderPaymentEventServiceImpl implements OrderPaymentEventService {

    private final OrderRepository orderRepository;
    private final OrderCustomerService orderCustomerService;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderValidator orderValidator;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;
    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;
    private final OrderBackorderSupport orderBackorderSupport;
    private final OrderDateTimeHelper orderDateTimeHelper;

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
            // QR hết hạn / thu ngân huỷ: đơn quay về nháp để chọn lại phương thức thanh toán (không mất giỏ).
            case "PAYMENT_EXPIRED" -> handlePaymentSessionEnded(
                    event,
                    "Mã QR chuyển khoản đã hết hạn sau 15 phút, vui lòng chọn lại phương thức thanh toán"
            );
            case "PAYMENT_CANCELLED" -> handlePaymentSessionEnded(
                    event,
                    "Thanh toán chuyển khoản đã bị huỷ, vui lòng chọn lại phương thức thanh toán"
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
        Order order = getExistingOrderForPaymentSuccess(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            if (isInventoryProcessingRetryable(order)) {
                finalizePaidOrder(order, event, order.getStatus(), null, paymentCausationId(event));
            } else {
                orderPaymentSagaService.completePaymentSaga(order, event, paymentCausationId(event));
            }
            return;
        }

        // Tiền về cho đơn đã huỷ / hết hạn: không tự hoàn tất, giữ lại để kiểm tra tay và hoàn tiền nếu cần.
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED) {
            markNeedReview(order, event, "Tiền chuyển khoản về sau khi đơn đã "
                    + (order.getStatus() == OrderStatus.CANCELLED ? "huỷ" : "hết hạn")
                    + ", mã thanh toán: " + firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
            return;
        }

        BigDecimal amountPaid = orderAmountCalculator.defaultZero(event.getAmountPaid());
        orderValidator.validatePaymentAmount(order, amountPaid);

        if (orderBackorderSupport.isAwaitingStock(order)) {
            handleBackorderPaymentSucceeded(order, event);
            return;
        }

        // Bước "Paid": ghi nhận đã thanh toán trước, trừ kho và hoàn tất đơn ở finalizePaidOrder.
        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                "Thanh toán chuyển khoản thành công, mã thanh toán: " + savedOrder.getPaymentCode(),
                null
        );

        finalizePaidOrder(savedOrder, event, oldStatus, null, paymentCausationId(event));
    }

    @Override
    public boolean finalizePaidOrder(
            Order order,
            PaymentOrderEventPayload event,
            OrderStatus statusBeforePayment,
            UUID changedBy,
            String causationId
    ) {
        boolean immediateConsume = isImmediateConsume(order);
        processPaidOrderInventory(order, event, immediateConsume);

        if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED) {
            order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
            Order savedOrder = orderRepository.save(order);
            log.warn("Đơn {} đã thanh toán nhưng chưa trừ được tồn kho: {}", savedOrder.getOrderCode(),
                    savedOrder.getInventoryFailureReason());
            // Thu ngân vẫn cần biết tiền đã vào; đơn hoàn tất sau khi scheduler trừ kho được.
            orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                    savedOrder, event, statusBeforePayment, OrderStatus.PAYMENT_CONFIRMED,
                    event == null ? null : event.getSagaId());
            return false;
        }

        // Trà đóng theo yêu cầu: gửi yêu cầu đóng gói; đơn chỉ hoàn tất khi giao bundle (custom-bundle/handover).
        orderBackorderSupport.requestBundlePacking(order);
        boolean bundlePending = orderBackorderSupport.hasBundle(order);
        OrderStatus newStatus = immediateConsume && !bundlePending ? OrderStatus.COMPLETED : OrderStatus.PAYMENT_CONFIRMED;
        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);
        if (newStatus == OrderStatus.COMPLETED) {
            orderStatusHistoryWriter.write(
                    savedOrder,
                    OrderStatus.PAYMENT_CONFIRMED,
                    OrderStatus.COMPLETED,
                    "Đã trừ tồn kho, hoàn tất đơn",
                    changedBy
            );
        }

        UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, causationId);
        orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
        if (savedOrder.getCustomerId() != null) {
            orderCustomerService.updateCustomerPurchase(savedOrder.getId());
        }
        orderOutboxPublisher.publishPaymentCompletedOrchestration(savedOrder, causationId, sagaId.toString());
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder, event, statusBeforePayment, newStatus, sagaId.toString());
        return true;
    }

    @Override
    @Transactional
    public int retryFailedInventory() {
        List<Order> orders = orderRepository.findPaidOrdersWithFailedInventory();
        int completed = 0;
        for (Order candidate : orders) {
            try {
                Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(candidate.getId())
                        .orElse(null);
                if (order == null || !isInventoryProcessingRetryable(order)) {
                    continue;
                }
                if (finalizePaidOrder(order, null, OrderStatus.PENDING_PAYMENT, null, "inventory-retry")) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                log.warn("Thử lại trừ kho cho đơn {} thất bại: {}", candidate.getOrderCode(), exception.getMessage());
            }
        }
        return completed;
    }

    /** Đơn chờ hàng trả bằng chuyển khoản: đã nhận đủ tiền nhưng chưa trừ tồn, chưa hoàn tất đơn. */
    private void handleBackorderPaymentSucceeded(Order order, PaymentOrderEventPayload event) {
        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        orderBackorderSupport.enterWaitingStock(order);

        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                "Thanh toán chuyển khoản thành công, mã thanh toán: " + savedOrder.getPaymentCode()
                        + ". Đơn chờ hàng, giao sau khi có hàng",
                null
        );
        UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, paymentCausationId(event));
        orderOutboxPublisher.publishCouponUsageCommit(savedOrder, paymentCausationId(event), sagaId.toString());
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder,
                event,
                oldStatus,
                OrderStatus.PAYMENT_CONFIRMED,
                sagaId.toString()
        );
    }

    private void handlePaymentFailedEvent(
            PaymentOrderEventPayload event,
            OrderStatus targetOrderStatus,
            String defaultReason
    ) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(targetOrderStatus);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        String reason = firstNonBlank(event.getFailureReason(), defaultReason);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, targetOrderStatus, reason, null);
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

        markNeedReview(order, event, firstNonBlank(event.getFailureReason(), "Thanh toán cần kiểm tra thủ công"));
    }

    /**
     * Giữ đơn (không hoàn tất, không huỷ) và gắn cờ NEED_REVIEW: số tiền lệch hoặc tiền về sau khi QR hết hạn.
     * Quản lý kiểm tra rồi xác nhận bằng mark-paid, hoặc huỷ đơn và hoàn tiền.
     */
    private void markNeedReview(Order order, PaymentOrderEventPayload event, String reason) {
        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.NEED_REVIEW);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setExpiresAt(null);

        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, savedOrder.getStatus(), reason, null);
        orderPaymentSagaService.markPaymentNeedReview(savedOrder, event, reason);
        orderOutboxPublisher.publishOrderPaymentNeedReviewNotification(
                savedOrder, event, oldStatus, savedOrder.getStatus(), event.getSagaId(), reason);
    }

    /**
     * Phiên thanh toán chuyển khoản kết thúc mà chưa có tiền (hết hạn hoặc thu ngân huỷ): đơn quay về DRAFT với
     * hạn nháp mới để thu ngân chọn lại phương thức; không huỷ đơn, không mất giỏ hàng.
     */
    private void handlePaymentSessionEnded(PaymentOrderEventPayload event, String reason) {
        UUID orderId = UUID.fromString(event.getOrderId());
        Order order = getExistingOrder(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()
                || PaymentStatus.NEED_REVIEW == order.getPaymentStatus()
                || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        Order savedOrder = returnOrderToDraft(order, reason);
        orderPaymentSagaService.failPaymentSaga(savedOrder, event, reason);
        orderOutboxPublisher.publishOrderPaymentFailedNotification(
                savedOrder, event, OrderStatus.PENDING_PAYMENT, OrderStatus.DRAFT, event.getSagaId(), reason);
    }

    @Override
    public Order returnOrderToDraft(Order order, String reason) {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.DRAFT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentMethod(null);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());
        order.setExpiresAt(orderDateTimeHelper.nextDraftExpiresAt());
        Order savedOrder = orderRepository.save(order);
        orderStatusHistoryWriter.write(savedOrder, oldStatus, OrderStatus.DRAFT, reason, null);
        return savedOrder;
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
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(order.getFinalAmount());

        Order savedOrder = orderRepository.save(order);
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            orderStatusHistoryWriter.write(
                    savedOrder,
                    oldStatus,
                    OrderStatus.PENDING_PAYMENT,
                    "Đang chờ thanh toán",
                    null
            );
        }
        orderPaymentSagaService.markPaymentWaiting(savedOrder, event, "Đang chờ payment event thành công");
    }

    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private Order getExistingOrderForPaymentSuccess(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    public void processPaidOrderInventory(
            Order order,
            PaymentOrderEventPayload event,
            boolean immediateConsume) {
        if (order == null || !isInventoryProcessingRetryable(order)) {
            return;
        }
        if (order.isInventoryCompensationPending()) {
            try {
                if (completeInventoryCompensation(order)) {
                    order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
                    order.setInventoryProcessedAt(Instant.now());
                    return;
                }
            } catch (RuntimeException compensationFailure) {
                order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
                order.setInventoryFailureReason("Compensation pending: " + compensationFailure.getMessage());
                return;
            }
        }
        if (order.getInventoryProcessingAttempt() == 0 || order.getSellableCompensatedAt() != null) {
            order.setInventoryProcessingAttempt(order.getInventoryProcessingAttempt() + 1);
            order.setSellableCompensatedAt(null);
        }
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.PROCESSING);
        order.setInventoryFailureReason(null);
        if (immediateConsume) {
            fulfillImmediateSale(order);
            return;
        }
        Map<UUID, BigDecimal> decremented = new LinkedHashMap<>();
        List<InventoryClient.ProductionRequiredAlertRequest> thresholdAlerts = new ArrayList<>();
        boolean catalogCallUncertain = false;
        try {
            Map<UUID, InventoryClient.ReserveMaterialLineRequest> materialLines = new LinkedHashMap<>();
            Map<UUID, BigDecimal> variantQuantities = new LinkedHashMap<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    if (item.isCustomBundle()) {
                        continue; // nguyên liệu bundle được giữ/trừ qua yêu cầu đóng gói bên Inventory
                    }
                    variantQuantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add);
                }
            }
            for (var variantEntry : variantQuantities.entrySet()) {
                    UUID variantId = variantEntry.getKey();
                    BigDecimal quantity = variantEntry.getValue();
                    List<CatalogClient.BomItemResponse> bom = catalogClient.getBomItemsByVariantId(variantId);
                    if (bom == null || bom.isEmpty()) {
                        throw new IllegalStateException("No BOM for product variant " + variantId);
                    }
                    for (var bomItem : bom) {
                        UUID ingredientId = bomItem.ingredient().id();
                        BigDecimal waste = bomItem.wasteRate() == null ? BigDecimal.ZERO : bomItem.wasteRate();
                        BigDecimal required = quantity.multiply(bomItem.quantity())
                                .multiply(BigDecimal.ONE.add(waste));
                        var previous = materialLines.get(ingredientId);
                        materialLines.put(ingredientId, new InventoryClient.ReserveMaterialLineRequest(
                                null, variantId, ingredientId, null, null,
                                previous == null ? required : previous.quantity().add(required)));
                    }
            }
            for (var variantEntry : variantQuantities.entrySet()) {
                UUID variantId = variantEntry.getKey();
                BigDecimal quantity = variantEntry.getValue();
                catalogCallUncertain = true;
                var adjustment = catalogClient.decrementSellableQuantity(variantId,
                        new CatalogClient.SellableQuantityAdjustmentRequest(quantity, null, "SALE",
                                "SALE:" + order.getId() + ":" + order.getInventoryProcessingAttempt()));
                catalogCallUncertain = false;
                decremented.put(variantId, quantity);
                if (adjustment != null && adjustment.thresholdCrossed()) {
                    thresholdAlerts.add(new InventoryClient.ProductionRequiredAlertRequest(variantId,
                            order.getBranchId(), adjustment.newSellableQuantity(),
                            adjustment.productionThreshold(), adjustment.productionBatchQuantity()));
                }
            }
            if (!materialLines.isEmpty()) {
                inventoryClient.reserveMaterials(new InventoryClient.ReserveMaterialOrderRequest(
                        order.getId(), order.getBranchId(), order.getInventoryProcessingAttempt(),
                        null, new ArrayList<>(materialLines.values())));
                thresholdAlerts.forEach(inventoryClient::createProductionRequiredAlert);
                if (immediateConsume) {
                    inventoryClient.consumeMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(order.getId()));
                }
            }
            order.setInventoryProcessingStatus(immediateConsume
                    ? OrderInventoryProcessingStatus.CONSUMED
                    : OrderInventoryProcessingStatus.RESERVED);
            order.setInventoryProcessedAt(Instant.now());
        } catch (RuntimeException ex) {
            if (!catalogCallUncertain && !decremented.isEmpty()) {
                order.setInventoryCompensationPending(true);
                try {
                    if (completeInventoryCompensation(order)) {
                        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
                        order.setInventoryProcessedAt(Instant.now());
                        return;
                    }
                } catch (RuntimeException compensationFailure) {
                    order.setInventoryFailureReason(ex.getMessage() + "; compensation: " + compensationFailure.getMessage());
                }
            }
            order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
            if (order.getInventoryFailureReason() == null) {
                order.setInventoryFailureReason(ex.getMessage());
            }
        }
    }

    /**
     * Đơn giao ngay tại quầy: tồn thật nằm ở Inventory nên trừ thành phẩm trên kệ (kệ thiếu thì Inventory xuất kho
     * lên kệ ngay, manager duyệt phiếu sau). sellable_quantity của Catalog chỉ là cấu hình, không bị trừ.
     * Nguyên liệu theo BOM chỉ được giữ chỗ cho việc sản xuất bù, không bị tiêu khi bán.
     * Toàn bộ nằm trong một transaction của Inventory nên lỗi thì không có gì cần bù trừ; gọi lại là an toàn.
     */
    private void fulfillImmediateSale(Order order) {
        try {
            Map<UUID, BigDecimal> variantQuantities = new LinkedHashMap<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
                    if (item.isCustomBundle()) {
                        continue; // nguyên liệu bundle được giữ/trừ qua yêu cầu đóng gói bên Inventory
                    }
                    variantQuantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add);
                }
            }
            if (variantQuantities.isEmpty()) {
                order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
                order.setInventoryProcessedAt(Instant.now());
                return;
            }
            List<InventoryClient.FulfillSaleLine> lines = variantQuantities.entrySet().stream()
                    .map(entry -> new InventoryClient.FulfillSaleLine(entry.getKey(), entry.getValue()))
                    .toList();

            InventoryClient.FulfillSaleResponse response = inventoryClient.fulfillSale(
                    new InventoryClient.FulfillSaleRequest(
                            order.getId(), order.getBranchId(), order.getInventoryProcessingAttempt(),
                            lines, restockMaterialLines(variantQuantities)));

            order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
            order.setInventoryProcessedAt(Instant.now());
            if (response != null && !response.alreadyProcessed()) {
                raiseProductionAlerts(order, response);
            }
        } catch (RuntimeException ex) {
            order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
            order.setInventoryFailureReason(ex.getMessage());
        }
    }

    /**
     * Nguyên liệu cần để sản xuất bù đúng số vừa bán. Mặt hàng không có BOM hoặc Catalog lỗi thì bỏ qua phần
     * nguyên liệu, không chặn việc bán thành phẩm.
     */
    private List<InventoryClient.FulfillSaleMaterialLine> restockMaterialLines(Map<UUID, BigDecimal> variantQuantities) {
        List<InventoryClient.FulfillSaleMaterialLine> materials = new ArrayList<>();
        for (var entry : variantQuantities.entrySet()) {
            List<CatalogClient.BomItemResponse> bom;
            try {
                bom = catalogClient.getBomItemsByVariantId(entry.getKey());
            } catch (RuntimeException ex) {
                log.warn("Cannot load BOM for variant {} to plan restock materials: {}", entry.getKey(), ex.getMessage());
                continue;
            }
            if (bom == null) {
                continue;
            }
            for (var bomItem : bom) {
                if (bomItem.ingredient() == null || bomItem.ingredient().id() == null || bomItem.quantity() == null) {
                    continue;
                }
                // wasteRate là phần trăm (0–100), cùng cách tính với lệnh sản xuất.
                BigDecimal wasteFactor = BigDecimal.ONE.add((bomItem.wasteRate() == null ? BigDecimal.ZERO : bomItem.wasteRate())
                        .divide(BigDecimal.valueOf(100)));
                BigDecimal required = entry.getValue().multiply(bomItem.quantity()).multiply(wasteFactor);
                if (required.compareTo(BigDecimal.ZERO) > 0) {
                    materials.add(new InventoryClient.FulfillSaleMaterialLine(
                            entry.getKey(), bomItem.ingredient().id(), required));
                }
            }
        }
        return materials;
    }

    /**
     * Cảnh báo cần sản xuất khi tồn thật (kệ + kho) sau khi bán xuống tới ngưỡng production_threshold.
     * Số đề xuất là phần còn thiếu để về lại mức sellable_quantity. Lỗi cảnh báo không làm hỏng đơn đã bán.
     */
    private void raiseProductionAlerts(Order order, InventoryClient.FulfillSaleResponse response) {
        if (response.lines() == null) {
            return;
        }
        for (var line : response.lines()) {
            try {
                var config = catalogClient.getVariantStockConfig(line.productVariantId());
                if (config == null || config.productionThreshold() == null) {
                    continue;
                }
                BigDecimal remaining = zeroIfNull(line.remainingShelfQuantity())
                        .add(zeroIfNull(line.remainingWarehouseQuantity()));
                if (remaining.compareTo(config.productionThreshold()) > 0) {
                    continue;
                }
                BigDecimal toTarget = zeroIfNull(config.sellableQuantity()).subtract(remaining);
                BigDecimal suggested = toTarget.compareTo(BigDecimal.ZERO) > 0
                        ? toTarget
                        : zeroIfNull(config.productionBatchQuantity());
                inventoryClient.createProductionRequiredAlert(new InventoryClient.ProductionRequiredAlertRequest(
                        line.productVariantId(), order.getBranchId(), remaining,
                        config.productionThreshold(), suggested));
            } catch (RuntimeException ex) {
                log.warn("Cannot raise production alert for variant {} of order {}: {}",
                        line.productVariantId(), order.getId(), ex.getMessage());
            }
        }
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean completeInventoryCompensation(Order order) {
        List<InventoryClient.MaterialReservationResponse> releaseResult = inventoryClient.releaseMaterials(
                new InventoryClient.ReleaseInventoryReservationOrderRequest(order.getId()));
        if (releaseResult != null && releaseResult.stream()
                .anyMatch(reservation -> "CONFIRMED".equals(reservation.status()))) {
            order.setInventoryCompensationPending(false);
            return true;
        }
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        order.getItems().stream()
                .filter(item -> !item.isCustomBundle())
                .forEach(item -> quantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add));
        quantities.forEach((variantId, quantity) -> catalogClient.restoreSellableQuantity(variantId,
                new CatalogClient.SellableQuantityAdjustmentRequest(quantity, null, "SALE_COMPENSATION",
                        "SALE_COMPENSATION:" + order.getId() + ":" + order.getInventoryProcessingAttempt())));
        order.setSellableCompensatedAt(Instant.now());
        order.setInventoryCompensationPending(false);
        return false;
    }

    private boolean isImmediateConsume(Order order) {
        return !order.isFulfillmentRequired();
    }

    private boolean isInventoryProcessingRetryable(Order order) {
        OrderInventoryProcessingStatus status = order.getInventoryProcessingStatus();
        return status == null
                || status == OrderInventoryProcessingStatus.NOT_STARTED
                || status == OrderInventoryProcessingStatus.FAILED;
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

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
