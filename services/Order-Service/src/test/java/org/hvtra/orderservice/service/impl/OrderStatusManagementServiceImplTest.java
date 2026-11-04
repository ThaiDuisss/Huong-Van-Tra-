package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.service.support.OrderBackorderSupport;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.outbox.service.OrderOutboxPublisher;
import org.hvtra.orderservice.service.support.OrderResponseAssembler;
import org.hvtra.orderservice.service.support.OrderStatusHistoryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderStatusManagementServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryWriter orderStatusHistoryWriter;

    @Mock
    private OrderResponseAssembler orderResponseAssembler;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderBackorderSupport orderBackorderSupport;
    @Mock
    private OrderOutboxPublisher orderOutboxPublisher;

    private OrderStatusManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStatusManagementServiceImpl(
                orderRepository,
                orderStatusHistoryWriter,
                orderResponseAssembler,
                catalogClient,
                inventoryClient,
                orderBackorderSupport,
                orderOutboxPublisher
        );
    }

    @Test
    void updateOrderStatusKeepsSaveHistoryAndResponseOrder() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        request.setNote("Hoàn tất đơn");
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateOrderStatus(orderId, request);

        assertSame(expected, actual);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        InOrder inOrder = inOrder(orderRepository, orderStatusHistoryWriter, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderStatusHistoryWriter).write(order, OrderStatus.PAYMENT_CONFIRMED, OrderStatus.COMPLETED, "Hoàn tất đơn", null);
        inOrder.verify(orderResponseAssembler).toResponse(order);
    }

    @Test
    void onlineCompletionConsumesReservedMaterialsBeforeCompleting() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("ONLINE");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.updateOrderStatus(orderId, request);

        verify(inventoryClient).consumeMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
        verify(orderOutboxPublisher).publishOrderCompleted(order, null, null);
        assertEquals(OrderInventoryProcessingStatus.CONSUMED, order.getInventoryProcessingStatus());
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void deliveryCompletionConsumesReservedMaterialsBeforeCompleting() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("DELIVERY");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.updateOrderStatus(orderId, request);

        verify(inventoryClient).consumeMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
        assertEquals(OrderInventoryProcessingStatus.CONSUMED, order.getInventoryProcessingStatus());
    }

    @Test
    void onlineCancelReleasesAndRestoresOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("ONLINE");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        OrderItem item = new OrderItem();
        item.setProductVariantId(variantId);
        item.setQuantity(java.math.BigDecimal.ONE);
        order.addItem(item);
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.updateOrderStatus(orderId, request);
        service.updateOrderStatus(orderId, request);

        verify(inventoryClient).releaseMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
        verify(catalogClient).restoreSellableQuantity(org.mockito.Mockito.eq(variantId), any());
        assertEquals(OrderInventoryProcessingStatus.RELEASED, order.getInventoryProcessingStatus());
    }

    @Test
    void deliveryCancelReleasesAndRestoresSellable() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("DELIVERY");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        OrderItem item = new OrderItem();
        item.setProductVariantId(variantId);
        item.setQuantity(java.math.BigDecimal.ONE);
        order.addItem(item);
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        service.updateOrderStatus(orderId, request);

        verify(inventoryClient).releaseMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(orderId));
        verify(catalogClient).restoreSellableQuantity(org.mockito.Mockito.eq(variantId), any());
    }

    @Test
    void onlineCancelRejectsUncompensatedFailedInventory() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("ONLINE");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> service.updateOrderStatus(orderId, request));

        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verifyNoInteractions(catalogClient, inventoryClient);
    }

    @Test
    void updateOrderStatusReturnsResponseWithoutSaveOrHistoryWhenStatusUnchanged() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.PENDING_PAYMENT);
        Order order = order(orderId, OrderStatus.PENDING_PAYMENT);
        OrderResponse expected = OrderResponse.builder().id(orderId).build();
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.of(order));
        when(orderResponseAssembler.toResponse(order)).thenReturn(expected);

        OrderResponse actual = service.updateOrderStatus(orderId, request);

        assertSame(expected, actual);
        InOrder inOrder = inOrder(orderRepository, orderResponseAssembler);
        inOrder.verify(orderRepository).findByIdAndIsDeletedFalse(orderId);
        inOrder.verify(orderResponseAssembler).toResponse(order);
        verifyNoInteractions(orderStatusHistoryWriter);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void updateOrderStatusKeepsExistingNotFoundMessageAndStopsSideEffects() {
        UUID orderId = UUID.randomUUID();
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdAndIsDeletedFalse(orderId)).thenReturn(Optional.empty());

        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> service.updateOrderStatus(orderId, request)
        );

        assertEquals("Không tìm thấy đơn hàng: " + orderId, exception.getMessage());
        verifyNoInteractions(orderStatusHistoryWriter, orderResponseAssembler);
        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void updateOrderStatusKeepsTransactionOnImplementation() throws NoSuchMethodException {
        Method method = OrderStatusManagementServiceImpl.class.getMethod(
                "updateOrderStatus",
                UUID.class,
                UpdateOrderStatusRequest.class
        );

        assertNotNull(method.getAnnotation(Transactional.class));
    }

    @ParameterizedTest
    @EnumSource(value = OrderInventoryProcessingStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"RESERVED"})
    void onlineCompletionRejectsEveryInventoryStateWithoutMaterialReservation(OrderInventoryProcessingStatus stock) {
        Order order = lockedPaidOnlineOrder(stock);
        assertThrows(IllegalStateException.class, () -> service.updateOrderStatus(order.getId(), statusRequest(OrderStatus.COMPLETED)));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verifyNoInteractions(inventoryClient, catalogClient, orderOutboxPublisher, orderStatusHistoryWriter);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void consumedMaterialsCannotBeCancelledAndCompensatedFailureDoesNotRestoreSellableTwice() {
        Order order = lockedPaidOnlineOrder(OrderInventoryProcessingStatus.CONSUMED);
        assertThrows(IllegalStateException.class, () -> service.updateOrderStatus(order.getId(), statusRequest(OrderStatus.CANCELLED)));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.FAILED);
        order.setSellableCompensatedAt(Instant.parse("2026-11-03T00:00:00Z"));
        when(orderRepository.save(order)).thenReturn(order);
        service.updateOrderStatus(order.getId(), statusRequest(OrderStatus.CANCELLED));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(OrderInventoryProcessingStatus.RELEASED, order.getInventoryProcessingStatus());
        verifyNoInteractions(inventoryClient, catalogClient, orderOutboxPublisher);
    }

    @Test
    void inventoryConfirmedReservationPreventsCancellationAndCatalogCompensation() {
        Order order = lockedPaidOnlineOrder(OrderInventoryProcessingStatus.RESERVED);
        when(inventoryClient.releaseMaterials(new InventoryClient.ReleaseInventoryReservationOrderRequest(order.getId())))
                .thenReturn(List.of(new InventoryClient.MaterialReservationResponse("RELEASED"),
                        new InventoryClient.MaterialReservationResponse("CONFIRMED")));
        assertThrows(IllegalStateException.class, () -> service.updateOrderStatus(order.getId(), statusRequest(OrderStatus.CANCELLED)));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        assertEquals(OrderInventoryProcessingStatus.RESERVED, order.getInventoryProcessingStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(catalogClient, orderOutboxPublisher, orderStatusHistoryWriter);
    }

    @Test
    void cancellationMergesVariantQuantitiesAndExcludesBundleContainerFromCatalogRestore() {
        Order order = lockedPaidOnlineOrder(OrderInventoryProcessingStatus.RESERVED);
        UUID variantId = UUID.randomUUID();
        OrderItem first = OrderItem.builder().productVariantId(variantId).quantity(new BigDecimal("2")).build();
        OrderItem second = OrderItem.builder().productVariantId(variantId).quantity(new BigDecimal("3")).build();
        OrderItem bundle = OrderItem.builder().productVariantId(UUID.randomUUID()).itemType(OrderItemType.CUSTOM_BUNDLE).quantity(BigDecimal.ONE).build();
        order.setItems(List.of(first, second, bundle));
        when(orderRepository.save(order)).thenReturn(order);
        service.updateOrderStatus(order.getId(), statusRequest(OrderStatus.CANCELLED));
        verify(catalogClient).restoreSellableQuantity(variantId, new CatalogClient.SellableQuantityAdjustmentRequest(
                new BigDecimal("5"), null, "ORDER_CANCEL", "ORDER_CANCEL:" + order.getId()));
        verify(catalogClient, never()).restoreSellableQuantity(eq(bundle.getProductVariantId()), any());
        assertEquals(OrderInventoryProcessingStatus.RELEASED, order.getInventoryProcessingStatus());
    }

    @Test
    void waitingStockBackorderCannotBypassDedicatedFulfillmentAction() {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.PAYMENT_CONFIRMED);
        order.setBackorder(true);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.WAITING_STOCK);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        assertThrows(InvalidOrderStatusException.class, () -> service.updateOrderStatus(id, statusRequest(OrderStatus.COMPLETED)));
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        verifyNoInteractions(inventoryClient, catalogClient, orderBackorderSupport, orderStatusHistoryWriter, orderOutboxPublisher);
    }

    @Test
    void missingLockedOrderStopsTransitionAfterInitialRead() {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("ONLINE");
        order.setPaymentStatus(PaymentStatus.PAID);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        assertThrows(OrderNotFoundException.class, () -> service.updateOrderStatus(id, statusRequest(OrderStatus.COMPLETED)));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryClient, catalogClient, orderOutboxPublisher);
    }

    @Test
    void bundleCompletionChecksPackingBeforeChangingStatusAndCancellationUsesPackingAction() {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.PAYMENT_CONFIRMED);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        when(orderBackorderSupport.hasBundle(order)).thenReturn(true);
        doThrow(new InvalidOrderStatusException("not packed")).when(orderBackorderSupport).requireBundlesPacked(order);
        assertThrows(InvalidOrderStatusException.class, () -> service.updateOrderStatus(id, statusRequest(OrderStatus.COMPLETED)));
        verify(orderBackorderSupport).refreshBundles(order);
        assertEquals(OrderStatus.PAYMENT_CONFIRMED, order.getStatus());
        when(orderRepository.save(order)).thenReturn(order);
        service.updateOrderStatus(id, statusRequest(OrderStatus.CANCELLED));
        verify(orderBackorderSupport).cancelBundlePacking(order, null);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    private Order lockedPaidOnlineOrder(OrderInventoryProcessingStatus stock) {
        UUID id = UUID.randomUUID();
        Order order = order(id, OrderStatus.PAYMENT_CONFIRMED);
        order.setOrderType("ONLINE");
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setInventoryProcessingStatus(stock);
        when(orderRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(id)).thenReturn(Optional.of(order));
        return order;
    }

    private UpdateOrderStatusRequest statusRequest(OrderStatus status) {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(status);
        return request;
    }

    private Order order(UUID orderId, OrderStatus status) {
        Order order = Order.builder()
                .status(status)
                .paymentStatus(PaymentStatus.WAITING_PAYMENT)
                .build();
        order.setId(orderId);
        return order;
    }
}
