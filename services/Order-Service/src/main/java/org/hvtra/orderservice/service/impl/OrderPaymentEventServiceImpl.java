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
        Order order = getExistingOrderForPaymentSuccess(orderId);

        if (PaymentStatus.PAID == order.getPaymentStatus()) {
            if (isInventoryProcessingRetryable(order)) {
                OrderStatus oldStatus = order.getStatus();
                processPaidOrderInventory(order, event, isImmediateConsume(order));
                if (order.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED) {
                    order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
                    orderRepository.save(order);
                    return;
                }
                OrderStatus newStatus = isImmediateConsume(order) ? OrderStatus.COMPLETED : OrderStatus.PAYMENT_CONFIRMED;
                order.setStatus(newStatus);
                Order savedOrder = orderRepository.save(order);
                orderStatusHistoryWriter.write(savedOrder, oldStatus, newStatus,
                        "Thanh toán đã xác nhận, xử lý tồn kho hoàn tất", null);
                UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, paymentCausationId(event));
                orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
                orderCustomerService.updateCustomerPurchase(savedOrder.getId());
                orderOutboxPublisher.publishPaymentCompletedOrchestration(savedOrder, paymentCausationId(event), sagaId.toString());
                orderOutboxPublisher.publishOrderPaymentSucceededNotification(savedOrder, event, oldStatus,
                        newStatus, sagaId.toString());
            } else {
                orderPaymentSagaService.completePaymentSaga(order, event, paymentCausationId(event));
            }
            return;
        }

        BigDecimal amountPaid = orderAmountCalculator.defaultZero(event.getAmountPaid());
        orderValidator.validatePaymentAmount(order, amountPaid);

        OrderStatus oldStatus = order.getStatus();
        order.setPaymentStatus(PaymentStatus.PAID);
        OrderStatus paidStatus = isImmediateConsume(order) ? OrderStatus.COMPLETED : OrderStatus.PAYMENT_CONFIRMED;
        order.setStatus(paidStatus);
        order.setPaymentMethod(firstNonBlank(
                orderResponseAssembler.normalizePaymentMethod(event.getPaymentMethod()),
                order.getPaymentMethod()
        ));
        order.setPaymentCode(firstNonBlank(event.getPaymentCode(), order.getPaymentCode()));
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        processPaidOrderInventory(order, event, isImmediateConsume(order));

        Order savedOrder = orderRepository.save(order);
        if (savedOrder.getInventoryProcessingStatus() == OrderInventoryProcessingStatus.FAILED) {
            savedOrder.setStatus(OrderStatus.PAYMENT_CONFIRMED);
            orderRepository.save(savedOrder);
            return;
        }
        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                paidStatus,
                "Thanh toán chuyển khoản thành công, mã thanh toán: " + savedOrder.getPaymentCode(),
                null
        );

        UUID sagaId = orderPaymentSagaService.completePaymentSaga(savedOrder, event, paymentCausationId(event));
        orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
        orderCustomerService.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(
                savedOrder,
                paymentCausationId(event),
                sagaId.toString()
        );
        orderOutboxPublisher.publishOrderPaymentSucceededNotification(
                savedOrder,
                event,
                oldStatus,
                paidStatus,
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
        String reason = firstNonBlank(event.getFailureReason(), "Thanh toán cần kiểm tra thủ công");
        if (oldStatus != OrderStatus.PENDING_PAYMENT) {
            orderStatusHistoryWriter.write(savedOrder, oldStatus, OrderStatus.PENDING_PAYMENT, reason, null);
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
        Map<UUID, BigDecimal> decremented = new LinkedHashMap<>();
        List<InventoryClient.ProductionRequiredAlertRequest> thresholdAlerts = new ArrayList<>();
        boolean catalogCallUncertain = false;
        try {
            Map<UUID, InventoryClient.ReserveMaterialLineRequest> materialLines = new LinkedHashMap<>();
            Map<UUID, BigDecimal> variantQuantities = new LinkedHashMap<>();
            if (order.getItems() != null) {
                for (var item : order.getItems()) {
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

    private boolean completeInventoryCompensation(Order order) {
        List<InventoryClient.MaterialReservationResponse> releaseResult = inventoryClient.releaseMaterials(
                new InventoryClient.ReleaseInventoryReservationOrderRequest(order.getId()));
        if (releaseResult != null && releaseResult.stream()
                .anyMatch(reservation -> "CONFIRMED".equals(reservation.status()))) {
            order.setInventoryCompensationPending(false);
            return true;
        }
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        order.getItems().forEach(item -> quantities.merge(item.getProductVariantId(), item.getQuantity(), BigDecimal::add));
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
