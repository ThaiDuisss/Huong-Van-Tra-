package org.hvtra.orderservice.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentMethod;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.event.PaymentOrderEventPayload;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.BackorderAvailabilityResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderBackorderService;
import org.hvtra.orderservice.service.OrderCustomerService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.OrderBackorderSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBackorderServiceImpl implements OrderBackorderService {

    private final OrderRepository orderRepository;
    private final OrderBackorderSupport orderBackorderSupport;
    private final OrderPaymentEventService orderPaymentEventService;
    private final OrderPaymentSagaService orderPaymentSagaService;
    private final OrderCustomerService orderCustomerService;
    private final OrderOutboxPublisher orderOutboxPublisher;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderMapper orderMapper;
    private final PaymentClient paymentClient;

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getWaitingOrders(String keyword, int page, int size) {
        String search = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Specification<Order> waiting = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.isTrue(root.get("backorder")));
            predicates.add(cb.equal(root.get("inventoryProcessingStatus"), OrderInventoryProcessingStatus.WAITING_STOCK));
            predicates.add(cb.equal(root.get("status"), OrderStatus.PAYMENT_CONFIRMED));
            if (search != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), search),
                        cb.like(cb.lower(root.get("customerNameSnapshot")), search),
                        cb.like(root.get("customerPhoneSnapshot"), search)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("expectedAvailableAt"), Sort.Order.asc("createdAt")));
        // Không gọi Payment-Service cho từng đơn: danh sách chỉ cần số liệu đã lưu trên đơn.
        return orderRepository.findAll(waiting, pageRequest)
                .map(order -> orderMapper.toResponse(order, null, BigDecimal.ZERO, order.getPaymentMethod()));
    }

    /**
     * Kiểm tra đơn đã đủ hàng để giao chưa. Dùng lệnh "chuẩn bị hàng" của Inventory thay vì chỉ ước tính để
     * tự khắc phục trường hợp bước chuẩn bị sau khi thu tiền bị lỗi (Inventory không tạo lệnh sản xuất thứ hai).
     */
    @Override
    @Transactional
    public BackorderAvailabilityResponse checkAvailability(UUID orderId) {
        Order order = getWaitingOrder(orderId);
        InventoryClient.AvailabilityEstimateResponse estimate = orderBackorderSupport.prepare(order);
        boolean ready = estimate != null && !estimate.backorder();
        if (estimate != null && estimate.backorder() && estimate.estimatedAvailableAt() != null
                && !estimate.estimatedAvailableAt().equals(order.getExpectedAvailableAt())) {
            order.setExpectedAvailableAt(estimate.estimatedAvailableAt());
            orderRepository.save(order);
        }
        return new BackorderAvailabilityResponse(
                order.getId(),
                ready,
                ready || estimate == null ? null : estimate.estimatedAvailableAt(),
                estimate == null || estimate.lines() == null ? List.of() : estimate.lines());
    }

    /**
     * Giao hàng cho đơn chờ: kiểm tra đủ hàng, trừ tồn, thu phần còn lại (nếu đơn mới đặt cọc) rồi hoàn tất đơn.
     * Trừ tồn trước khi thu tiền: nếu hết hàng thì chưa thu gì của khách. Inventory không trừ lần hai cho cùng
     * một đơn nên gọi lại sau lỗi là an toàn.
     */
    @Override
    @Transactional
    public OrderResponse fulfill(UUID orderId, FulfillBackorderRequest request) {
        Order order = getWaitingOrder(orderId);
        BigDecimal remaining = order.getDebtAmount() == null ? BigDecimal.ZERO : order.getDebtAmount();
        boolean settlementRequired = order.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID
                && remaining.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal receivedAmount = request == null ? null : request.getReceivedAmount();
        UUID changedBy = request == null ? null : request.getChangedBy();

        if (order.getPaymentStatus() != PaymentStatus.PAID && !settlementRequired) {
            throw new InvalidOrderStatusException("Đơn chưa thanh toán, không thể giao hàng");
        }
        if (settlementRequired && (receivedAmount == null || receivedAmount.compareTo(remaining) < 0)) {
            throw new InvalidPaymentAmountException(
                    "Số tiền khách đưa nhỏ hơn số tiền còn lại phải thu: " + remaining.toPlainString());
        }

        InventoryClient.AvailabilityEstimateResponse estimate = orderBackorderSupport.estimate(order);
        if (estimate != null && estimate.backorder()) {
            throw new InvalidOrderStatusException("Chưa đủ hàng để giao: " + missingItems(estimate));
        }

        OrderStatus oldStatus = order.getStatus();
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.NOT_STARTED);
        orderPaymentEventService.processPaidOrderInventory(order, null, true);
        if (order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.CONSUMED) {
            throw new InvalidOrderStatusException(
                    "Không trừ được tồn kho cho đơn chờ hàng: " + order.getInventoryFailureReason());
        }

        UUID sagaId;
        PaymentResponse payment = null;
        if (settlementRequired) {
            sagaId = orderPaymentSagaService.startPaymentSaga(order, PaymentMethod.CASH.name());
            payment = settleRemaining(order, receivedAmount, sagaId);
        } else {
            sagaId = orderPaymentSagaService.completePaymentSaga(order, null, order.getPaymentCode());
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.COMPLETED);
        Order savedOrder = orderRepository.save(order);

        orderStatusHistoryWriter.write(
                savedOrder,
                oldStatus,
                OrderStatus.COMPLETED,
                settlementRequired
                        ? "Đã có hàng: thu phần còn lại " + remaining.toPlainString() + " và giao hàng cho khách"
                        : "Đã có hàng: giao hàng cho khách",
                changedBy
        );

        PaymentOrderEventPayload paidEvent = paidEvent(savedOrder, payment, sagaId);
        if (settlementRequired) {
            orderPaymentSagaService.completePaymentSaga(savedOrder, paidEvent, savedOrder.getPaymentCode());
        }
        orderPaymentSagaService.markResourceConfirmationRequested(savedOrder, sagaId);
        orderCustomerService.updateCustomerPurchase(savedOrder.getId());
        orderOutboxPublisher.publishPaymentCompletedOrchestration(
                savedOrder, savedOrder.getPaymentCode(), sagaId.toString());
        return orderResponseAssembler.toResponse(savedOrder, payment);
    }

    private PaymentResponse settleRemaining(Order order, BigDecimal receivedAmount, UUID sagaId) {
        PaymentCreateRequest paymentRequest = new PaymentCreateRequest();
        paymentRequest.setOrderId(order.getId());
        paymentRequest.setOrderCode(order.getOrderCode());
        paymentRequest.setCustomerId(order.getCustomerId());
        paymentRequest.setSagaId(sagaId);
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);
        paymentRequest.setGoodsAmount(order.getOriginalAmount());
        paymentRequest.setDiscountAmount(order.getDiscountAmount());
        paymentRequest.setDeliveryFee(order.getShippingFee());
        paymentRequest.setFinalAmount(order.getFinalAmount());
        paymentRequest.setReceivedAmount(receivedAmount);
        paymentRequest.setDeliveryRequired(order.isFulfillmentRequired());

        PaymentResponse payment = paymentClient.checkout(paymentRequest);
        if (payment == null || payment.getStatus() == null || !"PAID".equalsIgnoreCase(payment.getStatus().trim())) {
            throw new InvalidPaymentAmountException("Payment-Service chưa xác nhận thu đủ phần còn lại của đơn");
        }
        return payment;
    }

    private PaymentOrderEventPayload paidEvent(Order order, PaymentResponse payment, UUID sagaId) {
        PaymentOrderEventPayload event = new PaymentOrderEventPayload();
        event.setSagaId(sagaId.toString());
        event.setOrderId(order.getId().toString());
        event.setOrderCode(order.getOrderCode());
        event.setPaymentId(payment == null || payment.getPaymentId() == null ? null : payment.getPaymentId().toString());
        event.setPaymentCode(order.getPaymentCode());
        event.setPaymentMethod(order.getPaymentMethod());
        event.setPaymentStatus(PaymentStatus.PAID.name());
        event.setAmountRequired(order.getFinalAmount());
        event.setAmountPaid(order.getFinalAmount());
        event.setEventType("PAYMENT_SUCCEEDED");
        event.setEventKey(order.getId().toString());
        return event;
    }

    private String missingItems(InventoryClient.AvailabilityEstimateResponse estimate) {
        if (estimate.lines() == null) {
            return "";
        }
        return estimate.lines().stream()
                .filter(line -> !"AVAILABLE".equals(line.scenario()))
                .map(line -> (line.itemName() == null ? String.valueOf(line.productVariantId()) : line.itemName())
                        + " thiếu " + (line.missingQuantity() == null ? "?" : line.missingQuantity().stripTrailingZeros().toPlainString()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private Order getWaitingOrder(UUID orderId) {
        Order order = orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
        if (!order.isBackorder()
                || order.getStatus() != OrderStatus.PAYMENT_CONFIRMED
                || order.getInventoryProcessingStatus() != OrderInventoryProcessingStatus.WAITING_STOCK) {
            throw new InvalidOrderStatusException("Đơn không ở trạng thái chờ hàng");
        }
        return order;
    }
}
