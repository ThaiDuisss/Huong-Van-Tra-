package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDraftIdentityContractTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VARIANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BOM = UUID.fromString("33333333-3333-3333-3333-333333333333");
    @Mock OrderRepository orders;
    @Mock ProductSnapshotResolver products;
    @Mock InventoryClient inventory;
    @Mock OrderCodeGenerator codes;
    @Mock OrderStatusHistoryWriter history;
    @Mock CustomerSnapshotResolver customers;
    @Mock OrderInventoryReservationService reservation;
    @Mock OrderResponseAssembler responses;
    @Mock OrderCheckoutPaymentProcessor checkout;
    private OrderDraftServiceImpl service;
    private Order draft;

    @BeforeEach void setUp() {
        var amounts = new OrderAmountCalculator();
        var dates = new OrderDateTimeHelper();
        var mapper = new OrderItemMapper();
        service = new OrderDraftServiceImpl(orders, new OrderMapper(mapper, amounts, dates), mapper,
                new OrderValidator(), amounts, codes, dates, history, customers, products, reservation, responses, checkout, inventory);
        draft = Order.builder().status(OrderStatus.DRAFT).paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .orderCode("DRAFT-UNIT").discountAmount(BigDecimal.ZERO).shippingFee(BigDecimal.ZERO).build();
        draft.setId(ID);
        when(orders.findByIdAndIsDeletedFalse(ID)).thenReturn(Optional.of(draft));
    }

    @ParameterizedTest
    @CsvSource({"same-empty,true", "same-bom-note,true", "different-bom,false", "different-note,false", "null-vs-bom,false", "bom-vs-null,false"})
    void draftLineIdentityUsesVariantBomAndNormalizedNoteWithoutRepricingExistingLine(String identity, boolean merge) {
        OrderItem original = item("2");
        original.setActualUnitPrice(new BigDecimal("10.25"));
        original.setDiscountAmount(BigDecimal.ONE);
        original.setBomVersionId(identity.equals("same-bom-note") || identity.equals("different-bom") || identity.equals("bom-vs-null") ? BOM : null);
        original.setNote(identity.equals("same-bom-note") ? " gift " : null);
        draft.addItem(original);
        AddDraftOrderItemRequest request = request();
        request.setQuantity(BigDecimal.ONE);
        request.setBomVersionId(identity.equals("same-bom-note") ? BOM : identity.equals("different-bom") || identity.equals("null-vs-bom") ? ID : null);
        request.setNote(identity.equals("same-bom-note") ? "gift" : identity.equals("different-note") ? "different" : " ");
        request.setAllowBackorder(true);
        when(products.resolvePosView(VARIANT)).thenReturn(variant("SKU-UNIT"));
        when(orders.save(draft)).thenReturn(draft);
        service.addDraftItem(request);
        assertEquals(merge ? 1 : 2, draft.getItems().size());
        assertEquals(new BigDecimal(merge ? "3" : "2"), original.getQuantity());
        assertEquals(new BigDecimal("10.25"), original.getActualUnitPrice());
        assertEquals(new BigDecimal(merge ? "29.75" : "19.50"), original.getLineTotal());
        assertEquals(merge ? new BigDecimal("29.75") : new BigDecimal("119.50"), draft.getFinalAmount());
        if (!merge) assertEquals("SKU-UNIT", draft.getItems().getLast().getSkuSnapshot());
        verifyNoInteractions(inventory, reservation, checkout, history);
    }

    @Test void aggregateQuantityAcrossDifferentNotesCannotExceedInventoryAvailability() {
        OrderItem first = item("2");
        first.setNote("first");
        OrderItem second = item("2");
        second.setId(BOM);
        second.setNote("second");
        draft.addItem(first);
        draft.addItem(second);
        AddDraftOrderItemRequest request = request();
        request.setQuantity(BigDecimal.ONE);
        request.setNote("second");
        when(products.resolvePosView(VARIANT)).thenReturn(variant("SKU-UNIT"));
        when(inventory.getStocksByVariantIds(any())).thenReturn(List.of(stock(VARIANT, new BigDecimal("4"))));
        assertThrows(InvalidOrderStatusException.class, () -> service.addDraftItem(request));
        assertEquals(new BigDecimal("2"), first.getQuantity());
        assertEquals(new BigDecimal("2"), second.getQuantity());
        verify(orders, never()).save(any());
        verifyNoInteractions(responses, checkout, reservation);
    }

    @ParameterizedTest @ValueSource(strings = {"null-response", "missing-variant", "null-available"})
    void missingAuthoritativeStockCannotBeTreatedAsSellable(String missing) {
        AddDraftOrderItemRequest request = request();
        request.setQuantity(BigDecimal.ONE);
        when(products.resolvePosView(VARIANT)).thenReturn(variant("SKU-UNIT"));
        when(inventory.getStocksByVariantIds(any())).thenReturn(missing.equals("null-response") ? null : List.of(
                stock(missing.equals("missing-variant") ? BOM : VARIANT, missing.equals("null-available") ? null : BigDecimal.TEN)));
        assertThrows(InvalidOrderStatusException.class, () -> service.addDraftItem(request));
        assertTrue(draft.getItems().isEmpty());
        verify(orders, never()).save(any());
        verifyNoInteractions(responses, reservation, checkout);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-1"})
    void invalidAddQuantityStopsBeforeInventoryAndPersistenceWithRealValidator(String value) {
        AddDraftOrderItemRequest request = request();
        request.setQuantity(value == null ? null : new BigDecimal(value));
        when(products.resolvePosView(VARIANT)).thenReturn(variant("SKU-UNIT"));
        assertThrows(InvalidOrderStatusException.class, () -> service.addDraftItem(request));
        verify(orders, never()).save(any());
        verifyNoInteractions(inventory, responses, reservation, checkout);
    }

    @Test void missingCatalogOrCustomBundleSkuCannotEnterRegularProductFlow() {
        AddDraftOrderItemRequest request = request();
        request.setQuantity(BigDecimal.ONE);
        IllegalArgumentException missing = new IllegalArgumentException("variant not found");
        when(products.resolvePosView(VARIANT)).thenThrow(missing);
        assertSame(missing, assertThrows(IllegalArgumentException.class, () -> service.addDraftItem(request)));
        doReturn(variant("CUSTOM-BUNDLE")).when(products).resolvePosView(VARIANT);
        assertThrows(InvalidOrderStatusException.class, () -> service.addDraftItem(request));
        verify(orders, never()).save(any());
        verifyNoInteractions(inventory, responses, reservation, checkout);
    }

    @Test void regularQuantityUpdateRetainsPriceAndDiscountAndBundleCannotChangeUnitCount() {
        OrderItem item = item("2");
        item.setActualUnitPrice(new BigDecimal("10.25"));
        item.setDiscountAmount(BigDecimal.ONE);
        draft.addItem(item);
        UpdateDraftOrderItemQuantityRequest request = new UpdateDraftOrderItemQuantityRequest();
        request.setQuantity(new BigDecimal("3"));
        request.setAllowBackorder(true);
        when(products.resolvePosView(VARIANT)).thenReturn(variant("SKU-UNIT"));
        when(orders.save(draft)).thenReturn(draft);
        service.updateDraftItemQuantity(ID, item.getId(), request);
        assertEquals(new BigDecimal("29.75"), draft.getFinalAmount());
        assertEquals(new BigDecimal("10.25"), item.getActualUnitPrice());
        item.setItemType(OrderItemType.CUSTOM_BUNDLE);
        assertThrows(InvalidOrderStatusException.class, () -> service.updateDraftItemQuantity(ID, item.getId(), request));
        verify(orders, times(1)).save(any());
        verifyNoInteractions(inventory, reservation, checkout);
    }

    private AddDraftOrderItemRequest request() {
        AddDraftOrderItemRequest request = new AddDraftOrderItemRequest();
        request.setOrderId(ID);
        request.setProductVariantId(VARIANT);
        return request;
    }
    private OrderItem item(String quantity) {
        OrderItem item = OrderItem.builder().productVariantId(VARIANT).quantity(new BigDecimal(quantity))
                .actualUnitPrice(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).build();
        item.setId(VARIANT);
        return item;
    }
    private CatalogClient.ProductVariantPosViewResponse variant(String sku) {
        return new CatalogClient.ProductVariantPosViewResponse(ID, VARIANT, sku, "Tea", "100g", "Tea", new BigDecimal("100"), BigDecimal.ZERO, null);
    }
    private InventoryClient.StockVariantBatchResponse stock(UUID id, BigDecimal available) {
        return new InventoryClient.StockVariantBatchResponse(id, null, available, null, null, null, null, null, null);
    }
}
