package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderDiscount;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.DiscountScope;
import org.hvtra.orderservice.domain.enums.DiscountType;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CreateDiscountRequest;
import org.hvtra.orderservice.dto.request.CreateOrderItemRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.response.CustomerResponse;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.repository.OrderDiscountRepository;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.hvtra.orderservice.service.OrderDraftService;
import org.hvtra.orderservice.service.OrderInventoryReservationService;
import org.hvtra.orderservice.service.OrderPaymentEventService;
import org.hvtra.orderservice.service.OrderPaymentSagaService;
import org.hvtra.orderservice.service.OrderPaymentService;
import org.hvtra.orderservice.service.OrderStatusManagementService;
import org.hvtra.orderservice.service.support.OrderCheckoutPaymentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceCreationTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.of(2026, 11, 3, 7, 0);
    @Mock private OrderRepository orders;
    @Mock private OrderStatusHistoryRepository histories;
    @Mock private CatalogClient catalog;
    @Mock private PaymentClient payment;
    @Mock private OrderCustomerServiceImpl customers;
    @Mock private OrderOutboxPublisher outbox;
    @Mock private OrderInventoryReservationService reservation;
    @Mock private OrderPaymentSagaService saga;
    @Mock private OrderDiscountRepository discounts;
    @Mock private OrderDraftService drafts;
    @Mock private OrderPaymentEventService paymentEvents;
    @Mock private OrderStatusManagementService status;
    @Mock private OrderCheckoutPaymentProcessor checkout;
    @Mock private OrderPaymentService paymentService;
    @InjectMocks private OrderServiceImpl service;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        variantId = UUID.randomUUID();
    }

    @Test
    void onlineCreationUsesCurrentCatalogPriceAndPersistsMoneyAndDiscountSnapshots() {
        CreateOrderRequest request = request("3");
        request.setShippingFee(new BigDecimal("5.005"));
        request.getItems().getFirst().setUnitPriceSnapshot(new BigDecimal("1"));
        request.getItems().getFirst().setItemDiscounts(List.of(discount(DiscountType.PERCENTAGE, "10")));
        request.setOrderDiscounts(List.of(discount(DiscountType.FIXED_AMOUNT, "10")));
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("100.005"));
        persistCreatedOrder();
        when(discounts.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        var response = atFixedTime(() -> service.createOrder(request));

        Order saved = capturedOrder();
        assertEquals(new BigDecimal("300.03"), saved.getOriginalAmount());
        assertEquals(new BigDecimal("40.00"), saved.getDiscountAmount());
        assertEquals(new BigDecimal("5.01"), saved.getShippingFee());
        assertEquals(new BigDecimal("265.04"), saved.getFinalAmount());
        assertEquals(new BigDecimal("265.04"), saved.getDebtAmount());
        assertEquals(BigDecimal.ZERO, saved.getPaidAmount());
        assertEquals(PaymentStatus.WAITING_PAYMENT, saved.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, saved.getStatus());
        assertEquals("ORD20261103070000000", saved.getOrderCode());
        var item = saved.getItems().getFirst();
        assertEquals(new BigDecimal("100.01"), item.getActualUnitPrice());
        assertEquals(new BigDecimal("270.03"), item.getLineTotal());
        assertEquals("SKU-UNIT", item.getSkuSnapshot());
        assertSame(saved, item.getOrder());
        assertEquals(saved.getFinalAmount(), response.getFinalAmount());
        assertEquals(2, saved.getDiscounts().size());
        OrderDiscount first = saved.getDiscounts().getFirst();
        OrderDiscount second = saved.getDiscounts().getLast();
        assertEquals(DiscountScope.ITEM, first.getScope());
        assertSame(item, first.getOrderItem());
        assertEquals(new BigDecimal("300.03"), first.getBaseAmount());
        assertEquals(new BigDecimal("30.00"), first.getAppliedAmount());
        assertEquals(DiscountScope.ORDER, second.getScope());
        assertEquals(new BigDecimal("270.03"), second.getBaseAmount());
        assertEquals(new BigDecimal("10.00"), second.getAppliedAmount());
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(histories).save(history.capture());
        assertEquals(NOW, history.getValue().getChangedAt());
        assertEquals(OrderStatus.PENDING_PAYMENT, history.getValue().getToStatus());
        verifyNoInteractions(reservation, checkout, outbox, saga);
    }

    @Test
    void offlineCreationRetainsPrintedPriceEvenWhenCatalogPriceChanged() {
        CreateOrderRequest request = request("2");
        request.setOfflineSale(true);
        request.setSoldAt(NOW.minusSeconds(3600));
        request.getItems().getFirst().setUnitPriceSnapshot(new BigDecimal("80.255"));
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("100"));
        persistCreatedOrder();
        atFixedTime(() -> service.createOrder(request));
        Order saved = capturedOrder();
        assertTrue(saved.isOfflineSale());
        assertEquals(request.getSoldAt(), saved.getSoldAt());
        assertEquals(new BigDecimal("100.00"), saved.getItems().getFirst().getOriginalUnitPrice());
        assertEquals(new BigDecimal("80.26"), saved.getItems().getFirst().getActualUnitPrice());
        assertEquals(new BigDecimal("160.52"), saved.getFinalAmount());
        verify(discounts, never()).saveAll(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void invalidQuantityCannotReachCatalogOrPersistenceWithoutBeanValidation(String quantity) {
        CreateOrderRequest request = request(quantity);
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(request)));
        verifyNoInteractions(catalog, orders, histories, discounts, reservation, checkout);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-1"})
    void missingOrNegativeCatalogPriceRejectsOrderBeforePersistence(String price) {
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant(price));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(request("1"))));
        verifyNoInteractions(orders, histories, discounts, reservation, checkout);
    }

    @Test
    void missingVariantAndNegativeShippingOrOfflinePriceCannotCreateOrder() {
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(request("1"))));
        CreateOrderRequest invalidShipping = request("1");
        invalidShipping.setShippingFee(new BigDecimal("-1"));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(invalidShipping)));
        CreateOrderRequest invalidPrice = request("1");
        invalidPrice.setOfflineSale(true);
        invalidPrice.getItems().getFirst().setUnitPriceSnapshot(BigDecimal.ONE.negate());
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("10"));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(invalidPrice)));
        verifyNoInteractions(orders, histories, discounts);
    }

    @Test
    void discountAboveLineAmountCannotPersistNegativeMoney() {
        CreateOrderRequest request = request("1");
        request.getItems().getFirst().setItemDiscounts(List.of(discount(DiscountType.FIXED_AMOUNT, "10.01")));
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("10"));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(request)));
        verifyNoInteractions(orders, histories, discounts);
    }

    @Test
    void customerLookupFailureUsesExplicitSnapshotAndPaymentLookupFailureDoesNotUndoCreation() {
        CreateOrderRequest request = request("1");
        request.setCustomerId(UUID.randomUUID());
        request.setCustomerNameSnapshot("Offline customer");
        when(customers.getCustomerById(request.getCustomerId())).thenThrow(new IllegalStateException("customer unavailable"));
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("10"));
        persistCreatedOrder();
        when(payment.getPaymentByOrderId(any())).thenThrow(new IllegalStateException("payment unavailable"));
        var response = atFixedTime(() -> service.createOrder(request));
        assertEquals("Offline customer", capturedOrder().getCustomerNameSnapshot());
        assertEquals("Offline customer", response.getCustomerName());
        assertEquals(new BigDecimal("10.00"), response.getDebtAmount());
    }

    @Test
    void authoritativeCustomerSnapshotOverridesClientSuppliedName() {
        CreateOrderRequest request = request("1");
        request.setCustomerId(UUID.randomUUID());
        request.setCustomerNameSnapshot("Client name");
        CustomerResponse customer = new CustomerResponse();
        customer.setFullName("Authoritative name");
        when(customers.getCustomerById(request.getCustomerId())).thenReturn(customer);
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("10"));
        persistCreatedOrder();
        atFixedTime(() -> service.createOrder(request));
        assertEquals("Authoritative name", capturedOrder().getCustomerNameSnapshot());
    }

    @Test
    void existingNonDraftCannotBeCheckedOutAgainThroughCreateEndpoint() {
        UUID id = UUID.randomUUID();
        Order terminal = Order.builder().status(OrderStatus.COMPLETED).paymentStatus(PaymentStatus.PAID).build();
        when(orders.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(terminal));
        CreateOrderRequest request = request("1");
        request.setOrderId(id);
        assertThrows(InvalidOrderStatusException.class, () -> service.createOrder(request));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(catalog, payment, checkout);
    }

    @Test
    void existingDraftCheckoutUsesSavedPriceReplacesDiscountsAndDelegatesPaymentWithCurrentActor() {
        Order draft = draft(new BigDecimal("3"));
        UUID tenant = UUID.randomUUID();
        draft.setTenantId(tenant);
        OrderDiscount oldDiscount = new OrderDiscount();
        oldDiscount.setId(UUID.randomUUID());
        draft.setDiscounts(new ArrayList<>(List.of(oldDiscount)));
        List<OrderDiscount> removed = new ArrayList<>();
        doAnswer(call -> { removed.addAll(call.getArgument(0)); return null; }).when(discounts).deleteAll(any());
        when(discounts.saveAll(any())).thenAnswer(call -> call.getArgument(0));
        when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(Optional.of(draft));
        when(orders.saveAndFlush(draft)).thenReturn(draft);
        CreateOrderRequest request = request("3");
        request.setOrderId(draft.getId());
        request.setPaymentMethod(" cash ");
        request.setPaidAmount(new BigDecimal("300"));
        request.setShippingFee(new BigDecimal("5"));
        request.getItems().getFirst().setUnitPriceSnapshot(new BigDecimal("999"));
        request.getItems().getFirst().setItemDiscounts(List.of(discount(DiscountType.PERCENTAGE, "10")));
        request.setOrderDiscounts(List.of(discount(DiscountType.FIXED_AMOUNT, "10")));
        UUID actor = UUID.randomUUID();
        PaymentResponse paymentResponse = PaymentResponse.builder().status("WAITING_PAYMENT").paymentCode("PAY-NEW").build();
        when(checkout.checkoutPayment(draft, "CASH", request.getPaidAmount(), actor)).thenReturn(paymentResponse);
        try (MockedStatic<SecurityUtils> identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::currentUserId).thenReturn(actor.toString());
            var response = atFixedTime(() -> service.createOrder(request));
            assertSame(paymentResponse, response.getPayment());
        }
        assertEquals(List.of(oldDiscount), removed);
        assertEquals(2, draft.getDiscounts().size());
        assertEquals(new BigDecimal("300.00"), draft.getOriginalAmount());
        assertEquals(new BigDecimal("40.00"), draft.getDiscountAmount());
        assertEquals(new BigDecimal("265.00"), draft.getFinalAmount());
        assertEquals(new BigDecimal("265.00"), draft.getDebtAmount());
        assertEquals(new BigDecimal("100"), draft.getItems().getFirst().getActualUnitPrice());
        assertEquals(tenant, draft.getTenantId());
        assertNull(draft.getExpiresAt());
        assertEquals(OrderStatus.PENDING_PAYMENT, draft.getStatus());
        assertEquals("POS-CASH-ORDER-UNIT", draft.getPaymentCode());
        verify(checkout).checkoutPayment(draft, "CASH", request.getPaidAmount(), actor);
        verifyNoInteractions(catalog, reservation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-variant", "duplicate", "missing-item", "quantity-mismatch"})
    void checkoutMustMatchEveryDraftLineExactlyOnceBeforeStartingPayment(String mismatch) {
        Order draft = draft(new BigDecimal("3"));
        when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(Optional.of(draft));
        CreateOrderRequest request = request("3");
        request.setOrderId(draft.getId());
        request.setPaymentMethod("CASH");
        switch (mismatch) {
            case "unknown-variant" -> request.getItems().getFirst().setProductVariantId(UUID.randomUUID());
            case "duplicate" -> request.setItems(List.of(request.getItems().getFirst(), request.getItems().getFirst()));
            case "missing-item" -> {
                OrderItem extra = new OrderItem();
                extra.setId(UUID.randomUUID());
                extra.setProductVariantId(UUID.randomUUID());
                draft.addItem(extra);
            }
            case "quantity-mismatch" -> request.getItems().getFirst().setQuantity(new BigDecimal("2"));
            default -> throw new AssertionError(mismatch);
        }
        atFixedTime(() -> assertThrows(InvalidOrderStatusException.class, () -> service.createOrder(request)));
        assertEquals(OrderStatus.DRAFT, draft.getStatus());
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(checkout, payment, catalog, reservation, histories);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    void draftCheckoutRejectsNonPositiveQuantityEvenWhenInvoiceContainsEnoughStock(String quantity) {
        Order draft = draft(new BigDecimal("3"));
        when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(Optional.of(draft));
        CreateOrderRequest request = request(quantity);
        request.setOrderId(draft.getId());
        atFixedTime(() -> assertThrows(InvalidOrderStatusException.class, () -> service.createOrder(request)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(checkout, payment, catalog);
    }

    @ParameterizedTest
    @ValueSource(strings = {"new-backorder", "new-deposit", "draft-backorder", "draft-deposit"})
    void backorderOrPositiveDepositUsesDedicatedProcessorWithExactMoneyAndActor(String route) {
        CreateOrderRequest request = request("1");
        request.setPaymentMethod(" transfer ");
        request.setPaidAmount(new BigDecimal("120.50"));
        request.setAllowBackorder(route.endsWith("backorder"));
        request.setDepositAmount(route.endsWith("deposit") ? new BigDecimal("20.25") : null);
        if (route.startsWith("draft")) {
            Order draft = draft(BigDecimal.ONE);
            request.setOrderId(draft.getId());
            when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(Optional.of(draft));
            when(orders.saveAndFlush(draft)).thenReturn(draft);
        } else {
            when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("100"));
            persistCreatedOrder();
        }
        UUID actor = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PaymentResponse pending = PaymentResponse.builder().status("WAITING_PAYMENT").build();
        when(checkout.checkoutBackorderPayment(any(), eq("BANK_TRANSFER"), eq(request.getPaidAmount()),
                eq(actor), eq(request.getDepositAmount()))).thenReturn(pending);
        try (MockedStatic<SecurityUtils> identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::currentUserId).thenReturn(actor.toString());
            assertSame(pending, atFixedTime(() -> service.createOrder(request)).getPayment());
        }
        Order saved = capturedOrder();
        verify(checkout).checkoutBackorderPayment(saved, "BANK_TRANSFER", request.getPaidAmount(), actor, request.getDepositAmount());
        verify(checkout, never()).checkoutPayment(any(), any(), any(), any());
        verifyNoInteractions(reservation, saga, outbox);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "expired", "missing", "no-payment", "negative-shipping"})
    void draftCheckoutGuardsPreventPaymentAndPersistence(String invalid) {
        Order draft = draft(BigDecimal.ONE);
        CreateOrderRequest request = request("1");
        request.setOrderId(draft.getId());
        request.setPaymentMethod("CASH");
        if (invalid.equals("empty")) draft.getItems().clear();
        if (invalid.equals("expired")) draft.setExpiresAt(NOW.minusSeconds(1));
        if (invalid.equals("no-payment")) request.setPaymentMethod(" ");
        if (invalid.equals("negative-shipping")) request.setShippingFee(new BigDecimal("-1"));
        when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(
                invalid.equals("missing") ? Optional.empty() : Optional.of(draft));
        atFixedTime(() -> assertThrows(RuntimeException.class, () -> service.createOrder(request)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(checkout, payment, reservation, saga, outbox, histories);
    }

    @Test
    void expiryAtExactInstantStillAllowsCheckoutAndProcessorFailureDoesNotPublishCompletion() {
        Order draft = draft(BigDecimal.ONE);
        draft.setExpiresAt(NOW);
        when(orders.findByIdAndIsDeletedFalse(draft.getId())).thenReturn(Optional.of(draft));
        when(orders.saveAndFlush(draft)).thenReturn(draft);
        CreateOrderRequest request = request("1");
        request.setOrderId(draft.getId());
        request.setPaymentMethod("CASH");
        UUID actor = UUID.fromString("11111111-1111-1111-1111-111111111111");
        IllegalStateException unavailable = new IllegalStateException("payment unavailable");
        when(checkout.checkoutPayment(draft, "CASH", null, actor)).thenThrow(unavailable);
        try (MockedStatic<SecurityUtils> identity = mockStatic(SecurityUtils.class)) {
            identity.when(SecurityUtils::currentUserId).thenReturn(actor.toString());
            assertSame(unavailable, atFixedTime(() -> assertThrows(IllegalStateException.class, () -> service.createOrder(request))));
        }
        assertEquals(PaymentStatus.WAITING_PAYMENT, draft.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, draft.getPaidAmount());
        verify(histories).save(any(OrderStatusHistory.class));
        verifyNoInteractions(outbox, saga, reservation);
        // Mockito does not model rollback; only the Spring transaction can undo the pre-payment write.
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "missing-type", "missing-value", "zero", "negative", "over-percent"})
    void invalidDiscountMetadataIsRejectedBeforeAnySaveOrPayment(String invalid) {
        CreateOrderRequest request = request("1");
        CreateDiscountRequest discount = discount(DiscountType.PERCENTAGE, "10");
        switch (invalid) {
            case "null" -> discount = null;
            case "missing-type" -> discount.setDiscountType(null);
            case "missing-value" -> discount.setDiscountValue(null);
            case "zero" -> discount.setDiscountValue(BigDecimal.ZERO);
            case "negative" -> discount.setDiscountValue(new BigDecimal("-1"));
            case "over-percent" -> discount.setDiscountValue(new BigDecimal("100.01"));
            default -> throw new AssertionError(invalid);
        }
        request.setOrderDiscounts(Collections.singletonList(discount));
        when(catalog.getProductVariantPosView(variantId)).thenReturn(variant("100"));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class, () -> service.createOrder(request)));
        verifyNoInteractions(orders, histories, discounts, checkout, reservation, outbox);
    }

    private Order draft(BigDecimal quantity) {
        Order draft = Order.builder().orderCode("ORDER-UNIT").status(OrderStatus.DRAFT)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT).build();
        draft.setId(UUID.randomUUID());
        draft.setCreatedAt(NOW);
        OrderItem item = OrderItem.builder().productVariantId(variantId).quantity(quantity).actualUnitPrice(new BigDecimal("100")).build();
        item.setId(UUID.randomUUID());
        draft.addItem(item);
        return draft;
    }

    private void persistCreatedOrder() {
        when(orders.saveAndFlush(any())).thenAnswer(call -> {
            Order saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(NOW);
            return saved;
        });
    }

    private Order capturedOrder() {
        ArgumentCaptor<Order> captured = ArgumentCaptor.forClass(Order.class);
        verify(orders).saveAndFlush(captured.capture());
        return captured.getValue();
    }

    private CreateOrderRequest request(String quantity) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.setProductVariantId(variantId);
        item.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(item));
        return request;
    }

    private CatalogClient.ProductVariantPosViewResponse variant(String price) {
        return new CatalogClient.ProductVariantPosViewResponse(UUID.randomUUID(), variantId, "SKU-UNIT", "Tea", "100g", "Tea",
                price == null ? null : new BigDecimal(price), BigDecimal.TEN, null);
    }

    private CreateDiscountRequest discount(DiscountType type, String value) {
        var discount = new CreateDiscountRequest();
        discount.setDiscountType(type);
        discount.setDiscountValue(new BigDecimal(value));
        return discount;
    }

    private <T> T atFixedTime(Supplier<T> action) {
        try (MockedStatic<Instant> instant = mockStatic(Instant.class, CALLS_REAL_METHODS);
             MockedStatic<LocalDateTime> local = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(NOW);
            local.when(LocalDateTime::now).thenReturn(LOCAL_NOW);
            return action.get();
        }
    }
}
