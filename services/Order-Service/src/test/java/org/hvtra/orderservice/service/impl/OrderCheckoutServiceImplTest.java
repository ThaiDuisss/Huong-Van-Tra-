package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CreateOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderAmountCalculator orderAmountCalculator;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

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
        service = new OrderCheckoutServiceImpl(
                orderRepository,
                orderMapper,
                orderItemMapper,
                orderAmountCalculator,
                orderCodeGenerator,
                orderStatusHistoryWriter,
                customerSnapshotResolver,
                productSnapshotResolver,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
    }

    @Test
    void createOrderWithPaymentKeepsSnapshotCalculationSaveHistoryAndPaymentOrder() {
        UUID customerId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        CreateOrderItemRequest firstRequest = itemRequest(new BigDecimal("2"), new BigDecimal("1000.00"));
        CreateOrderItemRequest secondRequest = itemRequest(BigDecimal.ONE, BigDecimal.ZERO);
        CreateOrderRequest request = request(List.of(firstRequest, secondRequest), " cash ");
        request.setCustomerId(customerId);
        request.setChangedBy(changedBy);
        request.setPaidAmount(new BigDecimal("38500.00"));
        request.setOrderType(" online ");
        request.setDiscountAmount(new BigDecimal("1000.00"));
        request.setShippingFee(new BigDecimal("500.00"));

        CustomerResponse customer = new CustomerResponse();
        CatalogClient.ProductVariantPosViewResponse firstVariant = variant(
                firstRequest.getProductVariantId(),
                new BigDecimal("10000.00")
        );
        CatalogClient.ProductVariantPosViewResponse secondVariant = variant(
                secondRequest.getProductVariantId(),
                new BigDecimal("20000.00")
        );
        Order order = order();
        OrderItem firstItem = new OrderItem();
        OrderItem secondItem = new OrderItem();
        PaymentResponse payment = PaymentResponse.builder().status("PAID").method("CASH").build();
        OrderResponse expected = OrderResponse.builder().id(UUID.randomUUID()).build();

        when(orderAmountCalculator.defaultZero(any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(customerSnapshotResolver.resolve(customerId)).thenReturn(customer);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("ORD-001");
        when(orderMapper.toOrder(
                request,
                customer,
                "ORD-001",
                "ONLINE",
                new BigDecimal("1000.00"),
                new BigDecimal("500.00")
        )).thenReturn(order);
        when(productSnapshotResolver.resolvePosView(firstRequest.getProductVariantId())).thenReturn(firstVariant);
        when(productSnapshotResolver.resolvePosView(secondRequest.getProductVariantId())).thenReturn(secondVariant);
        when(orderAmountCalculator.calculateCreationLineTotal(
                new BigDecimal("10000.00"),
                new BigDecimal("2"),
                new BigDecimal("1000.00")
        )).thenReturn(new BigDecimal("19000.00"));
        when(orderAmountCalculator.calculateCreationLineTotal(
                new BigDecimal("20000.00"),
                BigDecimal.ONE,
                BigDecimal.ZERO
        )).thenReturn(new BigDecimal("20000.00"));
        when(orderItemMapper.toOrderItem(
                firstVariant,
                new BigDecimal("2"),
                new BigDecimal("10000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("19000.00"),
                firstRequest.getBomVersionId()
        )).thenReturn(firstItem);
        when(orderItemMapper.toOrderItem(
                secondVariant,
                BigDecimal.ONE,
                new BigDecimal("20000.00"),
                BigDecimal.ZERO,
                new BigDecimal("20000.00"),
                secondRequest.getBomVersionId()
        )).thenReturn(secondItem);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.normalizePaymentMethod(" cash ")).thenReturn("CASH");
        when(orderCheckoutPaymentProcessor.checkoutPayment(
                order,
                "CASH",
                new BigDecimal("38500.00"),
                changedBy
        )).thenReturn(payment);
        when(orderResponseAssembler.toResponse(order, payment)).thenReturn(expected);

        OrderResponse result = service.createOrder(request);

        assertSame(expected, result);
        assertEquals(2, order.getItems().size());
        assertSame(firstItem, order.getItems().get(0));
        assertSame(secondItem, order.getItems().get(1));
        verify(orderRepository, times(2)).save(order);

        InOrder inOrder = inOrder(
                orderAmountCalculator,
                customerSnapshotResolver,
                orderCodeGenerator,
                orderMapper,
                productSnapshotResolver,
                orderItemMapper,
                orderRepository,
                orderStatusHistoryWriter,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("1000.00"));
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("500.00"));
        inOrder.verify(customerSnapshotResolver).resolve(customerId);
        inOrder.verify(orderCodeGenerator).generateOrderCode();
        inOrder.verify(orderMapper).toOrder(
                request,
                customer,
                "ORD-001",
                "ONLINE",
                new BigDecimal("1000.00"),
                new BigDecimal("500.00")
        );
        inOrder.verify(productSnapshotResolver).resolvePosView(firstRequest.getProductVariantId());
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("2"));
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("10000.00"));
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("1000.00"));
        inOrder.verify(orderAmountCalculator).calculateCreationLineTotal(
                new BigDecimal("10000.00"),
                new BigDecimal("2"),
                new BigDecimal("1000.00")
        );
        inOrder.verify(orderItemMapper).toOrderItem(
                firstVariant,
                new BigDecimal("2"),
                new BigDecimal("10000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("19000.00"),
                firstRequest.getBomVersionId()
        );
        inOrder.verify(productSnapshotResolver).resolvePosView(secondRequest.getProductVariantId());
        inOrder.verify(orderAmountCalculator).defaultZero(BigDecimal.ONE);
        inOrder.verify(orderAmountCalculator).defaultZero(new BigDecimal("20000.00"));
        inOrder.verify(orderAmountCalculator).defaultZero(BigDecimal.ZERO);
        inOrder.verify(orderAmountCalculator).calculateCreationLineTotal(
                new BigDecimal("20000.00"),
                BigDecimal.ONE,
                BigDecimal.ZERO
        );
        inOrder.verify(orderItemMapper).toOrderItem(
                secondVariant,
                BigDecimal.ONE,
                new BigDecimal("20000.00"),
                BigDecimal.ZERO,
                new BigDecimal("20000.00"),
                secondRequest.getBomVersionId()
        );
        inOrder.verify(orderAmountCalculator).applyCreatedOrderTotals(
                order,
                new BigDecimal("39000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("500.00")
        );
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(
                order,
                null,
                OrderStatus.PENDING_PAYMENT,
                "Tạo đơn hàng",
                null
        );
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod(" cash ");
        inOrder.verify(orderCheckoutPaymentProcessor).checkoutPayment(
                order,
                "CASH",
                new BigDecimal("38500.00"),
                changedBy
        );
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderResponseAssembler).toResponse(order, payment);
    }

    @Test
    void createOrderWithoutPaymentKeepsCustomerFallbackAndSingleSave() {
        CreateOrderRequest request = request(List.of(), null);
        request.setCustomerId(UUID.randomUUID());
        request.setOrderType(null);
        Order order = order();
        OrderResponse expected = OrderResponse.builder().id(UUID.randomUUID()).build();

        when(orderAmountCalculator.defaultZero(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);
        when(customerSnapshotResolver.resolve(request.getCustomerId())).thenReturn(null);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("ORD-002");
        when(orderMapper.toOrder(
                request,
                null,
                "ORD-002",
                "POS",
                BigDecimal.ZERO,
                BigDecimal.ZERO
        )).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.normalizePaymentMethod(null)).thenReturn(null);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse result = service.createOrder(request);

        assertSame(expected, result);
        verify(customerSnapshotResolver).resolve(request.getCustomerId());
        verify(orderMapper).toOrder(request, null, "ORD-002", "POS", BigDecimal.ZERO, BigDecimal.ZERO);
        verify(orderRepository).save(order);
        verify(orderRepository, times(1)).save(order);
        verify(orderStatusHistoryWriter).write(
                order,
                null,
                OrderStatus.PENDING_PAYMENT,
                "Tạo đơn hàng",
                null
        );
        verifyNoInteractions(orderCheckoutPaymentProcessor);
    }

    @Test
    void invalidNullRequestKeepsExistingNullPointerFailureBeforeDependencies() {
        assertThrows(NullPointerException.class, () -> service.createOrder(null));

        verifyNoInteractions(
                orderRepository,
                orderMapper,
                orderItemMapper,
                orderAmountCalculator,
                orderCodeGenerator,
                orderStatusHistoryWriter,
                customerSnapshotResolver,
                productSnapshotResolver,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
    }

    @Test
    void productResolverFailureKeepsSameExceptionAndStopsBeforeSave() {
        CreateOrderItemRequest itemRequest = itemRequest(BigDecimal.ONE, BigDecimal.ZERO);
        CreateOrderRequest request = request(List.of(itemRequest), null);
        Order order = order();
        RuntimeException catalogFailure = new RuntimeException("catalog unavailable");

        when(orderAmountCalculator.defaultZero(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);
        when(customerSnapshotResolver.resolve(null)).thenReturn(null);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("ORD-003");
        when(orderMapper.toOrder(
                request,
                null,
                "ORD-003",
                "POS",
                BigDecimal.ZERO,
                BigDecimal.ZERO
        )).thenReturn(order);
        when(productSnapshotResolver.resolvePosView(itemRequest.getProductVariantId())).thenThrow(catalogFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createOrder(request));

        assertSame(catalogFailure, thrown);
        assertEquals("catalog unavailable", thrown.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(orderStatusHistoryWriter, orderCheckoutPaymentProcessor);
    }

    @Test
    void paymentFailureKeepsExistingRuntimeWrapperMessageCauseAndSingleSave() {
        CreateOrderRequest request = request(List.of(), "CASH");
        Order order = order();
        InvalidPaymentAmountException paymentFailure =
                new InvalidPaymentAmountException("Số tiền thanh toán nhỏ hơn thành tiền của đơn hàng");

        when(orderAmountCalculator.defaultZero(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);
        when(customerSnapshotResolver.resolve(null)).thenReturn(null);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("ORD-004");
        when(orderMapper.toOrder(
                request,
                null,
                "ORD-004",
                "POS",
                BigDecimal.ZERO,
                BigDecimal.ZERO
        )).thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.normalizePaymentMethod("CASH")).thenReturn("CASH");
        when(orderCheckoutPaymentProcessor.checkoutPayment(order, "CASH", null, null))
                .thenThrow(paymentFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.createOrder(request));

        assertSame(paymentFailure, thrown.getCause());
        assertEquals(paymentFailure.toString(), thrown.getMessage());
        verify(orderRepository, times(1)).save(order);
        verify(orderStatusHistoryWriter).write(
                order,
                null,
                OrderStatus.PENDING_PAYMENT,
                "Tạo đơn hàng",
                null
        );
        verify(orderResponseAssembler, never()).toResponse(any(Order.class));
    }

    @Test
    void createOrderKeepsTransactionalBoundaryOnPublicUseCaseMethod() throws NoSuchMethodException {
        Transactional transactional = OrderCheckoutServiceImpl.class
                .getMethod("createOrder", CreateOrderRequest.class)
                .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRED, transactional.propagation());
        assertNull(OrderServiceImpl.class
                .getMethod("createOrder", CreateOrderRequest.class)
                .getAnnotation(Transactional.class));
    }

    private CreateOrderRequest request(List<CreateOrderItemRequest> items, String paymentMethod) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(items);
        request.setPaymentMethod(paymentMethod);
        request.setDiscountAmount(BigDecimal.ZERO);
        request.setShippingFee(BigDecimal.ZERO);
        return request;
    }

    private CreateOrderItemRequest itemRequest(BigDecimal quantity, BigDecimal discountAmount) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductVariantId(UUID.randomUUID());
        item.setQuantity(quantity);
        item.setDiscountAmount(discountAmount);
        item.setBomVersionId(UUID.randomUUID());
        return item;
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
                null
        );
    }

    private Order order() {
        Order order = Order.builder()
                .orderCode("ORD")
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .originalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .build();
        order.setId(UUID.randomUUID());
        return order;
    }
}
