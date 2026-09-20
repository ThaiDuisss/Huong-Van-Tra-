package org.hvtra.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.DiscountScope;
import org.hvtra.orderservice.domain.enums.DiscountStatus;
import org.hvtra.orderservice.dto.request.CreateDiscountRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
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
    private final OrderDiscountRepository orderDiscountRepository;
    private final CustomerSnapshotResolver customerSnapshotResolver;
    private final ProductSnapshotResolver productSnapshotResolver;
    private final OrderResponseAssembler orderResponseAssembler;
    private final OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {

        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal totalItemDiscount = BigDecimal.ZERO;
        BigDecimal shippingFee = orderAmountCalculator.defaultZero(request.getShippingFee());
        CustomerResponse customerSnapshot = customerSnapshotResolver.resolve(request.getCustomerId());
        Order order = orderMapper.toOrder(
                request,
                customerSnapshot,
                orderCodeGenerator.generateOrderCode(),
                normalizeOrderType(request.getOrderType()),
                BigDecimal.ZERO,
                shippingFee
        );

        for (var itemRequest : request.getItems()) {
            var variant = productSnapshotResolver.resolvePosView(itemRequest.getProductVariantId());
            BigDecimal quantity = orderAmountCalculator.defaultZero(itemRequest.getQuantity());
            BigDecimal actualUnitPrice = orderAmountCalculator.defaultZero(variant.sellingPrice());
            BigDecimal grossLineAmount = actualUnitPrice
                    .multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal itemDiscount = orderAmountCalculator.calculateDiscounts(
                    grossLineAmount,
                    itemRequest.getItemDiscounts()
            );
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
                    itemRequest.getBomVersionId(),
                    itemRequest.getNote()
            );

            order.addItem(item);
            originalAmount = originalAmount.add(grossLineAmount);
            totalItemDiscount = totalItemDiscount.add(itemDiscount);
        }

        BigDecimal orderDiscountAmount = orderAmountCalculator.calculateDiscounts(
                originalAmount.subtract(totalItemDiscount),
                request.getOrderDiscounts()
        );
        BigDecimal totalDiscountAmount = totalItemDiscount.add(orderDiscountAmount);

        orderAmountCalculator.applyCreatedOrderTotals(
                order,
                originalAmount,
                totalDiscountAmount,
                shippingFee
        );

        Order savedOrder = orderRepository.save(order);
        saveDiscountDetails(savedOrder, request);
        orderStatusHistoryWriter.write(savedOrder, null, savedOrder.getStatus(), "Tạo đơn hàng", request.getCashierId());

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
                    request.getCashierId()
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

    private void saveDiscountDetails(Order order, CreateOrderRequest request) {
        List<OrderDiscount> entities = new ArrayList<>();

        for (int index = 0; index < request.getItems().size(); index++) {
            OrderItem item = order.getItems().get(index);
            BigDecimal grossLineAmount = item.getActualUnitPrice()
                    .multiply(item.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);

            entities.addAll(createDiscountEntities(
                    order,
                    item,
                    DiscountScope.ITEM,
                    grossLineAmount,
                    request.getItems().get(index).getItemDiscounts()
            ));
        }

        BigDecimal totalItemDiscount = order.getItems().stream()
                .map(OrderItem::getDiscountAmount)
                .map(orderAmountCalculator::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderDiscountBase = order.getOriginalAmount().subtract(totalItemDiscount);

        entities.addAll(createDiscountEntities(
                order,
                null,
                DiscountScope.ORDER,
                orderDiscountBase,
                request.getOrderDiscounts()
        ));

        if (!entities.isEmpty()) {
            List<OrderDiscount> savedDiscounts = orderDiscountRepository.saveAll(entities);
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

        for (CreateDiscountRequest request : requests) {
            BigDecimal appliedAmount = orderAmountCalculator.calculateDiscountAmount(remainingAmount, request);

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
            entity.setAppliedByNameSnapshot(order.getCashierNameSnapshot());

            entities.add(entity);
            remainingAmount = remainingAmount.subtract(appliedAmount);
        }

        return entities;
    }
}
