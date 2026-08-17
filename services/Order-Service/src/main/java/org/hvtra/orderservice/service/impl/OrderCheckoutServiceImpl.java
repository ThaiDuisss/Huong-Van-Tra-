package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderCheckoutService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.support.ProductSnapshotResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrderCheckoutServiceImpl implements OrderCheckoutService {

    private static final String DEFAULT_ORDER_TYPE = "POS";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderAmountCalculator orderAmountCalculator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderStatusHistoryWriter orderStatusHistoryWriter;
    private final CustomerSnapshotResolver customerSnapshotResolver;
    private final ProductSnapshotResolver productSnapshotResolver;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal discountAmount = orderAmountCalculator.defaultZero(request.getDiscountAmount());
        BigDecimal shippingFee = orderAmountCalculator.defaultZero(request.getShippingFee());
        CustomerResponse customerSnapshot = customerSnapshotResolver.resolve(request.getCustomerId());
        Order order = orderMapper.toOrder(
                request,
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                normalizeOrderType(request.getOrderType()),
                discountAmount,
                shippingFee
        );

        for (var itemRequest : request.getItems()) {
            var variant = productSnapshotResolver.resolvePosView(itemRequest.getProductVariantId());
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
        orderStatusHistoryWriter.write(savedOrder, null, savedOrder.getStatus(), "Tạo đơn hàng", null);

        String paymentMethod = orderResponseAssembler.normalizePaymentMethod(request.getPaymentMethod());
        if (paymentMethod == null) {
            return orderResponseAssembler.toResponse(savedOrder);
        }
        PaymentResponse payment = null;
        try {
            payment = orderCheckoutPaymentProcessor.checkoutPayment(
                    savedOrder,
                    paymentMethod,
                    request.getPaidAmount(),
                    request.getChangedBy()
            );
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }

        Order paidOrder = orderRepository.save(savedOrder);
        return orderResponseAssembler.toResponse(paidOrder, payment);
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) {
            return DEFAULT_ORDER_TYPE;
        }
        return orderType.trim().toUpperCase(Locale.ROOT);
    }
}
