package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.InvalidPaymentAmountException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderInventoryReservationService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.support.ProductSnapshotResolver;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDraftServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private OrderAmountCalculator orderAmountCalculator;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @Mock
    private OrderDateTimeHelper orderDateTimeHelper;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private CustomerSnapshotResolver customerSnapshotResolver;

    @Mock
    private ProductSnapshotResolver productSnapshotResolver;

    @Mock
    private OrderInventoryReservationService inventoryReservationService;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    @Mock
    private OrderCheckoutPaymentProcessor orderCheckoutPaymentProcessor;

    private OrderDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderDraftServiceImpl(
                orderRepository,
                orderMapper,
                orderItemMapper,
                orderValidator,
                orderAmountCalculator,
                orderCodeGenerator,
                orderDateTimeHelper,
                orderStatusHistoryWriter,
                customerSnapshotResolver,
                productSnapshotResolver,
                inventoryReservationService,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
    }

    @Test
    void addDraftItemCreatesDraftAndAddsNewItemInExistingOrder() {
        UUID customerId = UUID.randomUUID();
        UUID cashierId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID bomVersionId = UUID.randomUUID();
        UUID inventoryLocationId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-25T01:00:00Z");
        BigDecimal quantity = new BigDecimal("2");
        BigDecimal unitPrice = new BigDecimal("50000.00");
        BigDecimal discount = new BigDecimal("3000.00");
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();
        request.setCustomerId(customerId);
        request.setCashierId(cashierId);
        request.setProductVariantId(variantId);
        request.setQuantity(quantity);
        request.setDiscountAmount(discount);
        request.setBomVersionId(bomVersionId);
        request.setInventoryLocationId(inventoryLocationId);
        CustomerResponse customer = new CustomerResponse();
        Order draft = draftOrder(UUID.randomUUID());
        OrderItem item = draftItem(UUID.randomUUID(), variantId);
        OrderResponse expected = OrderResponse.builder().id(draft.getId()).build();
        CatalogClient.ProductVariantPosViewResponse variant = variant(variantId, unitPrice);
        when(orderDateTimeHelper.nextDraftExpiresAt()).thenReturn(expiresAt);
        when(customerSnapshotResolver.resolve(customerId)).thenReturn(customer);
        when(orderCodeGenerator.generateOrderCode()).thenReturn("ORD-001");
        when(orderMapper.toDraftOrder(request, customer, "ORD-001", expiresAt)).thenReturn(draft);
        when(orderRepository.saveAndFlush(draft)).thenReturn(draft);
        when(productSnapshotResolver.resolvePosView(variantId)).thenReturn(variant);
        when(orderValidator.requirePositiveQuantity(quantity)).thenReturn(quantity);
        when(orderAmountCalculator.defaultZero(variant.sellingPrice())).thenReturn(unitPrice);
        when(orderAmountCalculator.defaultZero(discount)).thenReturn(discount);
        when(orderItemMapper.toDraftOrderItem(variant, quantity, unitPrice, discount, bomVersionId, null))
                .thenReturn(item);
        when(orderRepository.save(draft)).thenReturn(draft);
        when(orderResponseAssembler.toResponse(draft)).thenReturn(expected);

        OrderResponse actual = service.addDraftItem(request);

        assertSame(expected, actual);
        assertEquals(expiresAt, draft.getExpiresAt());
        assertEquals(List.of(item), draft.getItems());
        assertSame(draft, item.getOrder());
        InOrder inOrder = inOrder(
                orderDateTimeHelper,
                customerSnapshotResolver,
                orderCodeGenerator,
                orderMapper,
                orderRepository,
                orderStatusHistoryWriter,
                productSnapshotResolver,
                orderValidator,
                orderAmountCalculator,
                orderItemMapper,
                orderResponseAssembler
        );
        inOrder.verify(orderDateTimeHelper).nextDraftExpiresAt();
        inOrder.verify(customerSnapshotResolver).resolve(customerId);
        inOrder.verify(orderCodeGenerator).generateOrderCode();
        inOrder.verify(orderMapper).toDraftOrder(request, customer, "ORD-001", expiresAt);
        inOrder.verify(orderRepository).saveAndFlush(draft);
        inOrder.verify(orderStatusHistoryWriter).write(draft, null, OrderStatus.DRAFT, "Tạo hóa đơn nháp POS", cashierId);
        inOrder.verify(productSnapshotResolver).resolvePosView(variantId);
        inOrder.verify(orderValidator).requirePositiveQuantity(quantity);
        inOrder.verify(orderAmountCalculator).defaultZero(variant.sellingPrice());
        inOrder.verify(orderAmountCalculator).defaultZero(discount);
        inOrder.verify(orderItemMapper).toDraftOrderItem(variant, quantity, unitPrice, discount, bomVersionId, null);
        inOrder.verify(orderAmountCalculator).recalculateLine(item);
        inOrder.verify(orderAmountCalculator).recalculateOrderTotals(draft);
        inOrder.verify(orderRepository).save(draft);
        inOrder.verify(orderResponseAssembler).toResponse(draft);
        verifyNoInteractions(inventoryReservationService);
    }

    @Test
    void updateDraftItemQuantityUpdatesReservationAmountAndTotals() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-25T01:05:00Z");
        BigDecimal targetQuantity = new BigDecimal("3");
        UpdateDraftOrderItemQuantityRequest request = new UpdateDraftOrderItemQuantityRequest();
        request.setQuantity(targetQuantity);
        Order order = draftOrder(orderId);
        OrderItem item = draftItem(itemId, variantId);
        order.addItem(item);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        CatalogClient.ProductVariantPosViewResponse variant = variant(variantId, BigDecimal.TEN);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderAmountCalculator.defaultZero(targetQuantity)).thenReturn(targetQuantity);
        when(orderDateTimeHelper.nextDraftExpiresAt()).thenReturn(expiresAt);
        when(productSnapshotResolver.resolvePosView(variantId)).thenReturn(variant);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateDraftItemQuantity(orderId, itemId, request);

        assertSame(expected, actual);
        assertEquals(targetQuantity, item.getQuantity());
        assertEquals(expiresAt, order.getExpiresAt());
        InOrder inOrder = inOrder(
                orderRepository,
                orderValidator,
                orderAmountCalculator,
                orderDateTimeHelper,
                productSnapshotResolver,
                orderResponseAssembler
        );
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateEditableDraft(any(Order.class), any(Instant.class));
        inOrder.verify(orderAmountCalculator).defaultZero(targetQuantity);
        inOrder.verify(orderDateTimeHelper).nextDraftExpiresAt();
        inOrder.verify(productSnapshotResolver).resolvePosView(variantId);
        inOrder.verify(orderAmountCalculator).recalculateLine(item);
        inOrder.verify(orderAmountCalculator).recalculateOrderTotals(order);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoInteractions(inventoryReservationService);
    }

    @Test
    void updateDraftItemQuantityZeroKeepsExistingRemoveFlowWithoutSelfTransactionalCall() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant firstExpiresAt = Instant.parse("2026-07-25T01:10:00Z");
        Instant secondExpiresAt = Instant.parse("2026-07-25T01:15:00Z");
        UpdateDraftOrderItemQuantityRequest request = new UpdateDraftOrderItemQuantityRequest();
        request.setQuantity(BigDecimal.ZERO);
        Order order = draftOrder(orderId);
        OrderItem item = draftItem(itemId, variantId);
        order.addItem(item);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId))
                .thenReturn(Optional.of(order), Optional.of(order));
        when(orderAmountCalculator.defaultZero(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);
        when(orderDateTimeHelper.nextDraftExpiresAt()).thenReturn(firstExpiresAt, secondExpiresAt);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateDraftItemQuantity(orderId, itemId, request);

        assertSame(expected, actual);
        assertTrue(order.getItems().isEmpty());
        assertEquals(secondExpiresAt, order.getExpiresAt());
        verify(orderRepository, times(2)).findByIdAndIsDeletedFalse(orderId);
        verify(orderValidator, times(2)).validateEditableDraft(any(Order.class), any(Instant.class));
        InOrder inOrder = inOrder(orderRepository, orderValidator, orderAmountCalculator, orderDateTimeHelper);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateEditableDraft(any(Order.class), any(Instant.class));
        inOrder.verify(orderAmountCalculator).defaultZero(BigDecimal.ZERO);
        inOrder.verify(orderDateTimeHelper).nextDraftExpiresAt();
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateEditableDraft(any(Order.class), any(Instant.class));
        inOrder.verify(orderDateTimeHelper).nextDraftExpiresAt();
        verifyNoInteractions(inventoryReservationService);
    }

    @Test
    void cancelDraftOrderReleasesReservationWritesHistoryAndReturnsResponse() {
        UUID orderId = UUID.randomUUID();
        Order order = draftOrder(orderId);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.cancelDraftOrder(orderId);

        assertSame(expected, actual);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        InOrder inOrder = inOrder(orderRepository, orderValidator, orderStatusHistoryWriter, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateDraftCancellation(order);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(order, OrderStatus.DRAFT, OrderStatus.CANCELLED, "Hủy hóa đơn nháp POS", null);
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoInteractions(inventoryReservationService);
    }

    @Test
    void addDraftItemRejectsQuantityAboveSellable() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-25T01:25:00Z");
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();
        request.setOrderId(orderId);
        request.setProductVariantId(variantId);
        request.setQuantity(new BigDecimal("3"));
        Order order = draftOrder(orderId);
        CatalogClient.ProductVariantPosViewResponse variant = variant(
                variantId,
                BigDecimal.TEN,
                new BigDecimal("2")
        );
        when(orderDateTimeHelper.nextDraftExpiresAt()).thenReturn(expiresAt);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(productSnapshotResolver.resolvePosView(variantId)).thenReturn(variant);
        when(orderValidator.requirePositiveQuantity(new BigDecimal("3"))).thenReturn(new BigDecimal("3"));

        InvalidOrderStatusException exception = assertThrows(
                InvalidOrderStatusException.class,
                () -> service.addDraftItem(request)
        );

        assertEquals("Số lượng bán vượt quá số lượng có thể bán", exception.getMessage());
        verifyNoInteractions(inventoryReservationService, orderItemMapper, orderResponseAssembler);
    }

    @Test
    void addDraftItemStopsBeforeInventoryWhenQuantityInvalid() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-25T01:20:00Z");
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();
        request.setOrderId(orderId);
        request.setProductVariantId(variantId);
        request.setQuantity(BigDecimal.ZERO);
        Order order = draftOrder(orderId);
        CatalogClient.ProductVariantPosViewResponse variant = variant(variantId, BigDecimal.TEN);
        InvalidOrderStatusException expected = new InvalidOrderStatusException("Số lượng phải lớn hơn 0");
        when(orderDateTimeHelper.nextDraftExpiresAt()).thenReturn(expiresAt);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(productSnapshotResolver.resolvePosView(variantId)).thenReturn(variant);
        when(orderValidator.requirePositiveQuantity(BigDecimal.ZERO)).thenThrow(expected);

        InvalidOrderStatusException actual = assertThrows(
                InvalidOrderStatusException.class,
                () -> service.addDraftItem(request)
        );

        assertSame(expected, actual);
        verifyNoInteractions(inventoryReservationService, orderItemMapper, orderResponseAssembler);
    }

    @Test
    void removeDraftItemKeepsExistingNotFoundMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.removeDraftItem(orderId, itemId)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(inventoryReservationService, orderAmountCalculator, orderResponseAssembler);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void checkoutDraftOrderKeepsValidateDiscountSavePaymentSaveAndResponseOrder() {
        UUID orderId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();
        CheckoutDraftOrderRequest request = new CheckoutDraftOrderRequest();
        request.setDiscountAmount(new BigDecimal("5000.00"));
        request.setPaymentMethod("cash");
        request.setPaymentCode("PAY-REQ-001");
        request.setPaidAmount(new BigDecimal("100000.00"));
        request.setChangedBy(changedBy);
        Order order = draftOrder(orderId);
        OrderItem item = draftItem(UUID.randomUUID(), UUID.randomUUID());
        order.addItem(item);
        PaymentResponse payment = PaymentResponse.builder()
                .paymentCode("PAY-001")
                .status("PAID")
                .method("CASH")
                .build();
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderAmountCalculator.defaultZero(request.getDiscountAmount())).thenReturn(request.getDiscountAmount());
        when(orderResponseAssembler.normalizePaymentMethod("cash")).thenReturn("CASH");
        when(orderRepository.save(order)).thenReturn(order);
        when(orderCheckoutPaymentProcessor.checkoutPayment(order, "CASH", request.getPaidAmount(), changedBy))
                .thenReturn(payment);
        when(orderResponseAssembler.toResponse(order, payment)).thenReturn(expected);

        OrderResponse actual = service.checkoutDraftOrder(orderId, request);

        assertSame(expected, actual);
        assertEquals(new BigDecimal("5000.00"), order.getDiscountAmount());
        assertEquals("CASH", order.getPaymentMethod());
        assertEquals("PAY-REQ-001", order.getPaymentCode());
        InOrder inOrder = inOrder(
                orderRepository,
                orderValidator,
                orderAmountCalculator,
                orderResponseAssembler,
                orderCheckoutPaymentProcessor
        );
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderValidator).validateEditableDraft(any(Order.class), any(Instant.class));
        inOrder.verify(orderValidator).validateDraftHasItems(order);
        inOrder.verify(orderAmountCalculator).defaultZero(request.getDiscountAmount());
        inOrder.verify(orderAmountCalculator).recalculateOrderTotals(order);
        inOrder.verify(orderResponseAssembler).normalizePaymentMethod("cash");
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderCheckoutPaymentProcessor).checkoutPayment(order, "CASH", request.getPaidAmount(), changedBy);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderResponseAssembler).toResponse(order, payment);
    }

    @Test
    void checkoutDraftOrderKeepsExistingNotFoundMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        CheckoutDraftOrderRequest request = new CheckoutDraftOrderRequest();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.checkoutDraftOrder(orderId, request)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(orderCheckoutPaymentProcessor, orderResponseAssembler);
    }

    @Test
    void checkoutDraftOrderStopsBeforePaymentWhenOrderIsNotEditableDraft() {
        UUID orderId = UUID.randomUUID();
        CheckoutDraftOrderRequest request = new CheckoutDraftOrderRequest();
        Order order = draftOrder(orderId);
        InvalidOrderStatusException expected = new InvalidOrderStatusException("ORDER_NOT_DRAFT");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(expected)
                .when(orderValidator)
                .validateEditableDraft(any(Order.class), any(Instant.class));

        InvalidOrderStatusException actual = assertThrows(
                InvalidOrderStatusException.class,
                () -> service.checkoutDraftOrder(orderId, request)
        );

        assertSame(expected, actual);
        verifyNoInteractions(orderCheckoutPaymentProcessor, orderResponseAssembler);
    }

    @Test
    void checkoutDraftOrderStopsBeforePaymentWhenDraftHasNoItems() {
        UUID orderId = UUID.randomUUID();
        CheckoutDraftOrderRequest request = new CheckoutDraftOrderRequest();
        Order order = draftOrder(orderId);
        InvalidOrderStatusException expected = new InvalidOrderStatusException("Chưa có sản phẩm trong đơn");
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(expected).when(orderValidator).validateDraftHasItems(order);

        InvalidOrderStatusException actual = assertThrows(
                InvalidOrderStatusException.class,
                () -> service.checkoutDraftOrder(orderId, request)
        );

        assertSame(expected, actual);
        verifyNoInteractions(orderCheckoutPaymentProcessor, orderResponseAssembler);
    }

    @Test
    void checkoutDraftOrderKeepsInvalidPaymentMethodMessage() {
        UUID orderId = UUID.randomUUID();
        CheckoutDraftOrderRequest request = new CheckoutDraftOrderRequest();
        request.setPaymentMethod(" ");
        Order order = draftOrder(orderId);
        order.addItem(draftItem(UUID.randomUUID(), UUID.randomUUID()));
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.normalizePaymentMethod(" ")).thenReturn(null);

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> service.checkoutDraftOrder(orderId, request)
        );

        assertEquals("Phương thức thanh toán không hợp lệ", exception.getMessage());
        verifyNoInteractions(orderCheckoutPaymentProcessor);
    }

    @Test
    void draftMethodsKeepTransactionsOnImplementation() throws NoSuchMethodException {
        assertTransactional("addDraftItem", AddDraftOrderItemRequest.class);
        assertTransactional(
                "updateDraftItemQuantity",
                UUID.class,
                UUID.class,
                UpdateDraftOrderItemQuantityRequest.class
        );
        assertTransactional("removeDraftItem", UUID.class, UUID.class);
        assertTransactional("cancelDraftOrder", UUID.class);
        assertTransactional("checkoutDraftOrder", UUID.class, CheckoutDraftOrderRequest.class);
    }

    private void assertTransactional(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = OrderDraftServiceImpl.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertNotNull(transactional);
    }

    private Order draftOrder(UUID orderId) {
        Order order = Order.builder()
                .status(OrderStatus.DRAFT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .items(new ArrayList<>())
                .build();
        order.setId(orderId);
        return order;
    }

    private OrderItem draftItem(UUID itemId, UUID variantId) {
        OrderItem item = OrderItem.builder()
                .productVariantId(variantId)
                .quantity(BigDecimal.ONE)
                .actualUnitPrice(BigDecimal.TEN)
                .discountAmount(BigDecimal.ZERO)
                .lineTotal(BigDecimal.TEN)
                .build();
        item.setId(itemId);
        return item;
    }

    private CatalogClient.ProductVariantPosViewResponse variant(UUID variantId, BigDecimal sellingPrice) {
        return variant(variantId, sellingPrice, new BigDecimal("999"));
    }

    private CatalogClient.ProductVariantPosViewResponse variant(
            UUID variantId,
            BigDecimal sellingPrice,
            BigDecimal sellableQuantity
    ) {
        return new CatalogClient.ProductVariantPosViewResponse(
                UUID.randomUUID(),
                variantId,
                "SKU-001",
                "Trà sen",
                "Size M",
                "Trà",
                sellingPrice,
                sellableQuantity,
                null
        );
    }
}
