package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderItemComponent;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.AddDraftBundleItemRequest;
import org.hvtra.orderservice.dto.request.FulfillBackorderRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftBundleItemRequest;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.service.OrderBackorderService;
import org.hvtra.orderservice.service.helper.OrderAmountCalculator;
import org.hvtra.orderservice.service.helper.OrderCodeGenerator;
import org.hvtra.orderservice.service.helper.OrderDateTimeHelper;
import org.hvtra.orderservice.service.mapper.OrderItemMapper;
import org.hvtra.orderservice.service.mapper.OrderMapper;
import org.hvtra.orderservice.service.support.CustomerSnapshotResolver;
import org.hvtra.orderservice.service.support.OrderCustomBundleSupport;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.hvtra.orderservice.service.validator.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCustomBundleServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock private OrderRepository orders;
    @Mock private OrderCodeGenerator codes;
    @Mock private OrderStatusHistoryWriter histories;
    @Mock private CustomerSnapshotResolver customers;
    @Mock private OrderResponseAssembler responses;
    @Mock private OrderCustomBundleSupport bundles;
    @Mock private OrderBackorderService backorders;
    private OrderCustomBundleServiceImpl service;

    @BeforeEach
    void setUp() {
        var calculator = new OrderAmountCalculator();
        var dates = new OrderDateTimeHelper();
        var mapper = new OrderMapper(new OrderItemMapper(), calculator, dates);
        service = new OrderCustomBundleServiceImpl(orders, mapper, new OrderValidator(), calculator, codes,
                dates, histories, customers, responses, bundles, backorders);
    }

    @Test
    void addsBundleToNewDraftWithServerPriceComponentsAndHeaderIdentity() {
        var request = new AddDraftBundleItemRequest();
        UUID branch = UUID.randomUUID();
        request.setBranchId(branch);
        request.setCustomerId(UUID.randomUUID());
        request.setCustomerNameSnapshot("Customer");
        request.setBundleName("  Custom tea  ");
        request.setNote("  keep sealed  ");
        when(codes.generateOrderCode()).thenReturn("ORD-BUNDLE-UNIT");
        when(orders.saveAndFlush(any())).thenAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        var variant = new OrderCustomBundleSupport.CatalogVariant(UUID.randomUUID(), "CUSTOM", "Tea", "Bundle");
        OrderItemComponent component = new OrderItemComponent();
        component.setIngredientId(UUID.randomUUID());
        component.setQuantity(new BigDecimal("50"));
        component.setUnitPrice(new BigDecimal("2"));
        component.setLineTotal(new BigDecimal("100"));
        when(bundles.customBundleVariant()).thenReturn(variant);
        when(bundles.price(null)).thenReturn(new OrderCustomBundleSupport.PricedBundle(List.of(component), new BigDecimal("100")));

        atFixedTime(() -> service.addDraftBundleItem(request));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        Order order = saved.getValue();
        assertEquals(branch, order.getBranchId());
        assertEquals(request.getCustomerId(), order.getCustomerId());
        assertEquals("Customer", order.getCustomerNameSnapshot());
        assertEquals(OrderStatus.DRAFT, order.getStatus());
        assertEquals(NOW.plusSeconds(900), order.getExpiresAt());
        assertEquals(new BigDecimal("100"), order.getFinalAmount());
        assertEquals(new BigDecimal("100"), order.getDebtAmount());
        var item = order.getItems().getFirst();
        assertEquals(OrderItemType.CUSTOM_BUNDLE, item.getItemType());
        assertEquals(BigDecimal.ONE, item.getQuantity());
        assertEquals(variant.id(), item.getProductVariantId());
        assertEquals("Custom tea", item.getVariantNameSnapshot());
        assertEquals("keep sealed", item.getNote());
        assertSame(item, component.getOrderItem());
        verify(histories).write(eq(order), isNull(), eq(OrderStatus.DRAFT), anyString(), isNull());
        verify(customers).resolve(request.getCustomerId());
    }

    @Test
    void updatingBundleReplacesOldComponentsAndRecalculatesMoney() {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.WAITING_PAYMENT);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setItemType(OrderItemType.CUSTOM_BUNDLE);
        item.setQuantity(BigDecimal.ONE);
        item.setVariantNameSnapshot("Original name");
        item.setNote("Original note");
        item.setComponents(new ArrayList<>(List.of(new OrderItemComponent())));
        order.setItems(List.of(item));
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        var component = new OrderItemComponent();
        component.setIngredientId(UUID.randomUUID());
        when(bundles.price(null)).thenReturn(new OrderCustomBundleSupport.PricedBundle(List.of(component), new BigDecimal("120")));
        atFixedTime(() -> service.updateDraftBundleItem(order.getId(), item.getId(), new UpdateDraftBundleItemRequest()));
        assertEquals(List.of(component), item.getComponents());
        assertSame(item, component.getOrderItem());
        assertEquals(new BigDecimal("120"), item.getActualUnitPrice());
        assertEquals(new BigDecimal("120"), order.getFinalAmount());
        assertEquals("Original name", item.getVariantNameSnapshot());
        assertEquals("Original note", item.getNote());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED", "PENDING_PAYMENT"})
    void paidOrTerminalOrdersCannotBeEditedAsDraft(OrderStatus status) {
        Order order = order(status, PaymentStatus.PAID);
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        var request = new AddDraftBundleItemRequest();
        request.setOrderId(order.getId());
        atFixedTime(() -> assertThrows(InvalidOrderStatusException.class, () -> service.addDraftBundleItem(request)));
        verifyNoInteractions(bundles, responses);
        verify(orders, never()).save(any());
    }

    @Test
    void expiredDraftCannotBeUpdatedAndUnknownBundleCannotBeReplaced() {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.WAITING_PAYMENT);
        order.setExpiresAt(NOW.minusSeconds(1));
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        atFixedTime(() -> assertThrows(InvalidOrderStatusException.class,
                () -> service.updateDraftBundleItem(order.getId(), UUID.randomUUID(), new UpdateDraftBundleItemRequest())));
        order.setExpiresAt(null);
        atFixedTime(() -> assertThrows(OrderNotFoundException.class,
                () -> service.updateDraftBundleItem(order.getId(), UUID.randomUUID(), new UpdateDraftBundleItemRequest())));
        verifyNoInteractions(bundles, responses);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT,PAID,CONSUMED", "PAYMENT_CONFIRMED,UNPAID,CONSUMED", "COMPLETED,PAID,CONSUMED",
            "PAYMENT_CONFIRMED,PAID,FAILED", "PAYMENT_CONFIRMED,PAID,PROCESSING"})
    void handoverRequiresPaidConfirmedOrderAndConsumedProductStock(OrderStatus status, PaymentStatus paid, OrderInventoryProcessingStatus stock) {
        Order order = order(status, paid);
        order.setInventoryProcessingStatus(stock);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        assertThrows(InvalidOrderStatusException.class, () -> service.handover(order.getId(), null));
        verify(bundles, never()).refreshPacking(order);
        verify(orders, never()).save(any());
        verifyNoInteractions(backorders, responses, histories);
    }

    @Test
    void successfulHandoverChecksPackingBeforeCompletingAndWritingHistory() {
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        when(orders.save(order)).thenReturn(order);
        service.handover(order.getId(), null);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        var sequence = inOrder(bundles, orders, histories);
        sequence.verify(orders).findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId());
        sequence.verify(bundles).hasBundle(order);
        sequence.verify(bundles).refreshPacking(order);
        sequence.verify(bundles).requireAllPacked(order);
        sequence.verify(orders).save(order);
        sequence.verify(histories).write(eq(order), eq(OrderStatus.PAYMENT_CONFIRMED), eq(OrderStatus.COMPLETED), anyString(), isNull());
    }

    @Test
    void unpackedBundleCannotBeMarkedCompletedOrWriteHistory() {
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        doThrow(new InvalidOrderStatusException("not packed")).when(bundles).requireAllPacked(order);
        assertThrows(InvalidOrderStatusException.class, () -> service.handover(order.getId(), null));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verify(orders, never()).save(order);
        verifyNoInteractions(histories);
    }

    @Test
    void waitingStockBackorderUsesExistingFulfillmentInsteadOfPrematureCompletion() {
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PARTIALLY_PAID);
        order.setBackorder(true);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.WAITING_STOCK);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        var request = new FulfillBackorderRequest();
        service.handover(order.getId(), request);
        verify(backorders).fulfill(order.getId(), request);
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verify(orders, never()).save(order);
    }

    @Test
    void absentOrderOrNonBundleCannotBeHandedOver() {
        assertThrows(OrderNotFoundException.class, () -> service.handover(UUID.randomUUID(), null));
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertThrows(InvalidOrderStatusException.class, () -> service.handover(order.getId(), null));
        verifyNoInteractions(backorders, histories);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PAID", "PARTIALLY_PAID"})
    void refreshPackingIsAllowedAfterFullPaymentOrDeposit(PaymentStatus payment) {
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, payment);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        service.refreshPacking(order.getId());
        verify(bundles).refreshPacking(order);
        verify(orders).save(order);
    }

    @Test
    void unpaidOrderCannotRequestPacking() {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.UNPAID);
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        assertThrows(InvalidOrderStatusException.class, () -> service.refreshPacking(order.getId()));
        verify(bundles, never()).refreshPacking(order);
        verify(orders, never()).save(order);
    }

    @Test
    void packedEventOnlyPersistsWhenExactOrderItemWasChanged() {
        UUID missing = UUID.randomUUID();
        assertFalse(service.markPacked(missing, UUID.randomUUID(), "PACK"));
        Order order = order(OrderStatus.PAYMENT_CONFIRMED, PaymentStatus.PAID);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        order.setItems(List.of(item));
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        assertFalse(service.markPacked(order.getId(), missing, "PACK"));
        when(bundles.markPacked(item, "PACK")).thenReturn(true, false);
        assertTrue(service.markPacked(order.getId(), item.getId(), "PACK"));
        assertFalse(service.markPacked(order.getId(), item.getId(), "PACK"));
        verify(orders).save(order);
    }

    @ParameterizedTest
    @CsvSource({"Updated tea,New note,Updated tea,New note", "' ',' ',Default bundle,"})
    void explicitBundleEditsNormalizeNameAndNoteWhileKeepingIngredientPriceAuthoritative(
            String name, String note, String expectedName, String expectedNote) {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.WAITING_PAYMENT);
        OrderItem item = OrderItem.builder().itemType(OrderItemType.CUSTOM_BUNDLE).quantity(BigDecimal.ONE)
                .variantNameSnapshot("Old name").note("Old note").build();
        item.setId(UUID.randomUUID());
        order.addItem(item);
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        when(bundles.price(null)).thenReturn(new OrderCustomBundleSupport.PricedBundle(List.of(), new BigDecimal("75.25")));
        when(bundles.customBundleVariant()).thenReturn(new OrderCustomBundleSupport.CatalogVariant(UUID.randomUUID(), "BUNDLE", "Tea", "Default bundle"));
        UpdateDraftBundleItemRequest request = new UpdateDraftBundleItemRequest();
        request.setBundleName(name);
        request.setNote(note);
        atFixedTime(() -> service.updateDraftBundleItem(order.getId(), item.getId(), request));
        assertEquals(expectedName, item.getVariantNameSnapshot());
        assertEquals(expectedNote, item.getNote());
        assertEquals(new BigDecimal("75.25"), order.getFinalAmount());
        assertEquals(NOW.plusSeconds(900), order.getExpiresAt());
        verifyNoInteractions(backorders, histories);
    }

    @Test
    void invalidRecipePriceStopsUpdateBeforeReplacingSnapshotOrSaving() {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.WAITING_PAYMENT);
        OrderItem item = OrderItem.builder().itemType(OrderItemType.CUSTOM_BUNDLE).quantity(BigDecimal.ONE)
                .actualUnitPrice(new BigDecimal("50")).variantNameSnapshot("Old name").build();
        item.setId(UUID.randomUUID());
        OrderItemComponent old = new OrderItemComponent();
        item.replaceComponents(List.of(old));
        order.addItem(item);
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        when(bundles.price(null)).thenThrow(new IllegalArgumentException("invalid ingredients/quantity"));
        atFixedTime(() -> assertThrows(IllegalArgumentException.class,
                () -> service.updateDraftBundleItem(order.getId(), item.getId(), new UpdateDraftBundleItemRequest())));
        assertEquals(List.of(old), item.getComponents());
        assertEquals(new BigDecimal("50"), item.getActualUnitPrice());
        assertEquals("Old name", item.getVariantNameSnapshot());
        verify(orders, never()).save(any());
        verifyNoInteractions(backorders, histories, responses);
    }

    @Test
    void ordinaryProductCannotBeEditedThroughBundleEndpointAndRefreshFailureCannotSave() {
        Order order = order(OrderStatus.DRAFT, PaymentStatus.PAID);
        OrderItem regular = OrderItem.builder().itemType(OrderItemType.PRODUCT).build();
        regular.setId(UUID.randomUUID());
        order.addItem(regular);
        when(orders.findByIdAndIsDeletedFalse(order.getId())).thenReturn(Optional.of(order));
        atFixedTime(() -> assertThrows(OrderNotFoundException.class,
                () -> service.updateDraftBundleItem(order.getId(), regular.getId(), new UpdateDraftBundleItemRequest())));
        when(orders.findByIdAndIsDeletedFalseWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(bundles.hasBundle(order)).thenReturn(true);
        IllegalStateException unavailable = new IllegalStateException("inventory unavailable");
        doThrow(unavailable).when(bundles).refreshPacking(order);
        assertSame(unavailable, assertThrows(IllegalStateException.class, () -> service.refreshPacking(order.getId())));
        verify(orders, never()).save(any());
        verify(bundles, never()).price(any());
        verifyNoInteractions(histories, backorders, responses);
    }

    private Order order(OrderStatus status, PaymentStatus payment) {
        Order order = Order.builder().status(status).paymentStatus(payment).build();
        order.setId(UUID.randomUUID());
        return order;
    }

    private <T> T atFixedTime(Supplier<T> action) {
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            return action.get();
        }
    }
}
