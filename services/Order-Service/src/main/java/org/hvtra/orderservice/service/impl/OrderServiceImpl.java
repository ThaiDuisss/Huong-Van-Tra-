package org.hvtra.orderservice.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.domain.entity.*;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.exception.*;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_STATUS_PENDING = "PENDING";
    private static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    private static final String PAYMENT_STATUS_UNPAID = "UNPAID";
    private static final String PAYMENT_STATUS_PAID = "PAID";
    private static final String FULFILLMENT_STATUS_PENDING = "PENDING";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CatalogClient catalogClient;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        BigDecimal originalAmount = BigDecimal.ZERO;

        // Khởi tạo order với trạng thái mặc định: chưa thanh toán
        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .tenantId(request.getTenantId())
                .branchId(request.getBranchId())
                .terminalId(request.getTerminalId())
                .shiftId(request.getShiftId())
                .customerId(request.getCustomerId())
                .orderType(request.getOrderType() == null ? "POS" : request.getOrderType())
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .fulfillmentStatus(FULFILLMENT_STATUS_PENDING)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(defaultZero(request.getDiscountAmount()))
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .build();

        // Duyệt từng sản phẩm trong request để tạo order item
        for (var itemRequest : request.getItems()) {
            var variant = catalogClient.getProductVariantPosView(itemRequest.getProductVariantId());
            BigDecimal quantity = defaultZero(itemRequest.getQuantity());
            BigDecimal actualUnitPrice = defaultZero(variant.sellingPrice());
            BigDecimal itemDiscount = defaultZero(itemRequest.getDiscountAmount());
            // Tính tổng tiền của từng dòng sản phẩm
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
            // Cộng lineTotal vào tổng tiền gốc của đơn hàng
            originalAmount = originalAmount.add(lineTotal);
        }

        BigDecimal discountAmount = defaultZero(request.getDiscountAmount());
        BigDecimal shippingFee = BigDecimal.ZERO;
        // Tính số tiền cuối cùng khách cần thanh toán
        BigDecimal finalAmount = originalAmount.subtract(discountAmount);
        // Nếu giảm giá vượt quá tổng tiền thì finalAmount không được âm
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setFinalAmount(finalAmount);
        // Khi mới tạo đơn, chưa thanh toán nên paidAmount = 0 và debtAmount = finalAmount
        order.setPaidAmount(BigDecimal.ZERO);
        order.setDebtAmount(finalAmount);
        // Lưu order và toàn bộ order items nhờ CascadeType.ALL
        Order savedOrder = orderRepository.save(order);
        // Lưu lịch sử trạng thái khi đơn hàng được tạo
        saveStatusHistory(
                savedOrder,
                null,
                ORDER_STATUS_PENDING,
                "Tạo đơn hàng",
                null
        );

        return toResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        Order order = getExistingOrder(orderId);
        return toResponse(order);
    }

    // Payment-Service dùng thông tin này để kiểm tra đơn trước khi thanh toán
    @Override
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
    // Không cho thanh toán lại đơn đã PAID
    @Override
    @Transactional
    public OrderResponse markOrderPaid(UUID orderId, MarkOrderPaidRequest request) {
        Order order = getExistingOrder(orderId);

        if (PAYMENT_STATUS_PAID.equalsIgnoreCase(order.getPaymentStatus())) {
            throw new OrderAlreadyPaidException("Đơn hàng đã được thanh toán");
        }

        BigDecimal paidAmount = defaultZero(request.getPaidAmount());
        // Kiểm tra số tiền Payment-Service gửi sang có đủ thanh toán đơn không
        if (paidAmount.compareTo(order.getFinalAmount()) < 0) {
            throw new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");
        }
        // Lưu trạng thái cũ để ghi vào order_status_history
        String oldStatus = order.getStatus();
        // Cập nhật đơn hàng sau khi Payment-Service đã lưu payment thành công
        order.setPaymentStatus(PAYMENT_STATUS_PAID);
        order.setStatus(ORDER_STATUS_COMPLETED);
        order.setPaidAmount(order.getFinalAmount());
        order.setDebtAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);
        // Lưu lịch sử chuyển trạng thái từ PENDING sang COMPLETED
        saveStatusHistory(
                savedOrder,
                oldStatus,
                ORDER_STATUS_COMPLETED,
                "Thanh toán thành công, mã thanh toán: " + request.getPaymentCode(),
                request.getChangedBy()
        );

        return toResponse(savedOrder);
    }
    // Chỉ lấy đơn chưa bị soft delete
    private Order getExistingOrder(UUID orderId) {
        return orderRepository.findByIdAndIsDeletedFalse(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }
    // Ghi lại lịch sử thay đổi trạng thái của đơn hàng
    private void saveStatusHistory(
            Order order,
            String fromStatus,
            String toStatus,
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

    private OrderResponse toResponse(Order order) {
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
                .fulfillmentStatus(order.getFulfillmentStatus())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .shippingFee(order.getShippingFee())
                .finalAmount(order.getFinalAmount())
                .paidAmount(order.getPaidAmount())
                .debtAmount(order.getDebtAmount())
                .items(order.getItems().stream().map(this::toItemResponse).toList())
                .build();
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariantId())
                .skuSnapshot(item.getSkuSnapshot())
                .productNameSnapshot(item.getProductNameSnapshot())
                .variantNameSnapshot(item.getVariantNameSnapshot())
                .quantity(item.getQuantity())
                .originalUnitPrice(item.getOriginalUnitPrice())
                .actualUnitPrice(item.getActualUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .lineTotal(item.getLineTotal())
                .bomVersionId(item.getBomVersionId())
                .build();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
    // Sinh mã đơn hàng tạm thời theo thời gian, ví dụ ORD20260614153000123
    private String generateOrderCode() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
