package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.enums.DiscountSource;
import org.hvtra.orderservice.domain.enums.DiscountType;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.dto.request.CreateDiscountRequest;
import org.hvtra.orderservice.dto.request.CreateOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.support.ProductSnapshotResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderDiscountRepository orderDiscountRepository;

    @Mock
    private CustomerSnapshotResolver customerSnapshotResolver;

    @Mock
    private ProductSnapshotResolver productSnapshotResolver;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    @Mock
    private OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    private OrderCheckoutServiceImpl service;

    @BeforeEach
    void setUp() {
        OrderAmountCalculator amountCalculator = new OrderAmountCalculator();
        OrderItemMapper itemMapper = new OrderItemMapper();
        service = new OrderCheckoutServiceImpl(
                orderRepository,
                new OrderMapper(itemMapper, amountCalculator, null),
                itemMapper,
                amountCalculator,
                new FixedOrderCodeGenerator(),
                orderStatusHistoryWriter,
                orderDiscountRepository,
                customerSnapshotResolver,
                productSnapshotResolver,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
    }

    @Test
    void createOrderUsesCurrentDiscountDtoAndKeepsOrderServiceTotals() {
        UUID cashierId = UUID.randomUUID();
        CreateOrderItemRequest item = itemRequest(new BigDecimal("2"), percentage("10"));
        CreateOrderRequest request = request(List.of(item), null);
        request.setCashierId(cashierId);
        request.setShippingFee(new BigDecimal("500.00"));
        request.setOrderDiscounts(List.of(fixed("1000.00")));
        OrderResponse expected = OrderResponse.builder().id(UUID.randomUUID()).build();
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        when(productSnapshotResolver.resolvePosView(item.getProductVariantId()))
                .thenReturn(variant(item.getProductVariantId(), new BigDecimal("10000.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderDiscountRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderResponseAssembler.normalizePaymentMethod(null)).thenReturn(null);
        when(orderResponseAssembler.toResponse(any(Order.class))).thenReturn(expected);

        OrderResponse result = service.createOrder(request);

        assertSame(expected, result);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals(new BigDecimal("20000.00"), savedOrder.getOriginalAmount());
        assertEquals(new BigDecimal("3000.00"), savedOrder.getDiscountAmount());
        assertEquals(new BigDecimal("17500.00"), savedOrder.getFinalAmount());
        assertEquals(cashierId, savedOrder.getCashierId());
        assertEquals(2, savedOrder.getDiscounts().size());
        verify(orderStatusHistoryWriter).write(
                savedOrder,
                null,
                OrderStatus.PENDING_PAYMENT,
                "Tạo đơn hàng",
                cashierId
        );
        verifyNoInteractions(orderCheckoutPaymentProcessor);
    }

    @Test
    void createOrderWithoutDiscountDoesNotPersistDiscountRows() {
        CreateOrderItemRequest item = itemRequest(BigDecimal.ONE);
        CreateOrderRequest request = request(List.of(item), null);
        OrderResponse expected = OrderResponse.builder().id(UUID.randomUUID()).build();

        when(productSnapshotResolver.resolvePosView(item.getProductVariantId()))
                .thenReturn(variant(item.getProductVariantId(), new BigDecimal("10000.00")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderResponseAssembler.normalizePaymentMethod(null)).thenReturn(null);
        when(orderResponseAssembler.toResponse(any(Order.class))).thenReturn(expected);

        OrderResponse result = service.createOrder(request);

        assertSame(expected, result);
        verify(orderDiscountRepository, never()).saveAll(anyList());
        verifyNoInteractions(orderCheckoutPaymentProcessor);
    }

    @Test
    void checkoutDelegatesPaymentWithNormalizedMethodAndPersistsAfterProcessorReturns() {
        CreateOrderItemRequest item = itemRequest(BigDecimal.ONE);
        CreateOrderRequest request = request(List.of(item), " cash ");
        request.setOrderType(" online ");
        request.setPaidAmount(new BigDecimal("10000"));
        when(productSnapshotResolver.resolvePosView(item.getProductVariantId())).thenReturn(variant(item.getProductVariantId(), new BigDecimal("10000")));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(orderResponseAssembler.normalizePaymentMethod(" cash ")).thenReturn("CASH");
        PaymentResponse payment = PaymentResponse.builder().status("PAID").build();
        when(orderCheckoutPaymentProcessor.checkoutPayment(any(), eq("CASH"), eq(request.getPaidAmount()), isNull())).thenReturn(payment);
        service.createOrder(request);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(saved.capture());
        Order order = saved.getValue();
        assertEquals("ONLINE", order.getOrderType());
        assertEquals(new BigDecimal("10000.00"), order.getFinalAmount());
        var sequence = inOrder(orderRepository, orderCheckoutPaymentProcessor, orderResponseAssembler);
        sequence.verify(orderRepository).save(order);
        sequence.verify(orderCheckoutPaymentProcessor).checkoutPayment(order, "CASH", request.getPaidAmount(), null);
        sequence.verify(orderRepository).save(order);
        sequence.verify(orderResponseAssembler).toResponse(order, payment);
    }

    @Test
    void processorFailurePropagatesAndDoesNotPersistPostPaymentStateOrBuildSuccessResponse() {
        CreateOrderItemRequest item = itemRequest(BigDecimal.ONE);
        CreateOrderRequest request = request(List.of(item), "CASH");
        when(productSnapshotResolver.resolvePosView(item.getProductVariantId())).thenReturn(variant(item.getProductVariantId(), BigDecimal.TEN));
        when(orderRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        RuntimeException failure = new IllegalStateException("payment failed");
        when(orderCheckoutPaymentProcessor.checkoutPayment(any(), eq("CASH"), isNull(), isNull())).thenThrow(failure);
        RuntimeException propagated = assertThrows(RuntimeException.class, () -> service.createOrder(request));
        assertSame(failure, propagated.getCause());
        verify(orderRepository).save(any());
        verify(orderResponseAssembler, never()).toResponse(any(), any());
    }

    private CreateOrderRequest request(List<CreateOrderItemRequest> items, String paymentMethod) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(items);
        request.setPaymentMethod(paymentMethod);
        request.setShippingFee(BigDecimal.ZERO);
        return request;
    }

    private CreateOrderItemRequest itemRequest(BigDecimal quantity, CreateDiscountRequest... discounts) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductVariantId(UUID.randomUUID());
        item.setQuantity(quantity);
        item.setItemDiscounts(List.of(discounts));
        item.setBomVersionId(UUID.randomUUID());
        return item;
    }

    private CreateDiscountRequest fixed(String amount) {
        return discount(DiscountType.FIXED_AMOUNT, amount);
    }

    private CreateDiscountRequest percentage(String percentage) {
        return discount(DiscountType.PERCENTAGE, percentage);
    }

    private CreateDiscountRequest discount(DiscountType type, String value) {
        CreateDiscountRequest discount = new CreateDiscountRequest();
        discount.setDiscountType(type);
        discount.setDiscountValue(new BigDecimal(value));
        discount.setSource(DiscountSource.MANUAL);
        discount.setReasonCode("MANUAL");
        return discount;
    }

    private CatalogClient.ProductVariantPosViewResponse variant(UUID variantId, BigDecimal sellingPrice) {
        return new CatalogClient.ProductVariantPosViewResponse(
                UUID.randomUUID(),
                variantId,
                "SKU",
                "Trà",
                "Size M",
                "Danh mục",
                sellingPrice,
                new BigDecimal("999"),
                null
        );
    }

    private static final class FixedOrderCodeGenerator extends OrderCodeGenerator {
        @Override
        public String generateOrderCode() {
            return "ORD-001";
        }
    }
}
