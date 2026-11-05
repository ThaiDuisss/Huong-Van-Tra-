package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderStatusHistory;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.repository.OrderRepository;
import org.hvtra.orderservice.repository.OrderStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    @Mock private OrderRepository orders;
    @Mock private OrderStatusHistoryRepository histories;
    @Mock private InventoryClient inventory;
    @InjectMocks private OrderExpirationServiceImpl service;

    @Test
    void expirationUsesControlledTimeAndDoesNothingWhenNoDraftHasExpired() {
        when(orders.findByStatusAndExpiresAtBeforeAndIsDeletedFalse(OrderStatus.DRAFT, NOW)).thenReturn(List.of());
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            assertEquals(0, service.expireDraftOrders());
        }
        verifyNoInteractions(inventory, histories);
    }

    @Test
    void releasesReservationBeforePersistingExpiryAndHistory() {
        Order order = draft();
        when(orders.findByStatusAndExpiresAtBeforeAndIsDeletedFalse(OrderStatus.DRAFT, NOW)).thenReturn(List.of(order));
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            assertEquals(1, service.expireDraftOrders());
        }
        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        var sequence = inOrder(inventory, orders, histories);
        sequence.verify(inventory).releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder()
                .orderId(order.getId()).build());
        sequence.verify(orders).save(order);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        sequence.verify(histories).save(history.capture());
        assertSame(order, history.getValue().getOrder());
        assertEquals(OrderStatus.DRAFT, history.getValue().getFromStatus());
        assertEquals(OrderStatus.EXPIRED, history.getValue().getToStatus());
        assertEquals(NOW, history.getValue().getChangedAt());
    }

    @Test
    void inventoryFailureKeepsFailedDraftEditableAndContinuesOtherExpiredOrders() {
        Order failed = draft();
        Order successful = draft();
        when(orders.findByStatusAndExpiresAtBeforeAndIsDeletedFalse(OrderStatus.DRAFT, NOW))
                .thenReturn(List.of(failed, successful));
        doThrow(new IllegalStateException("inventory unavailable")).when(inventory)
                .releaseOrder(InventoryClient.ReleaseInventoryReservationOrderRequest.builder().orderId(failed.getId()).build());
        try (MockedStatic<Instant> clock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            clock.when(Instant::now).thenReturn(NOW);
            assertEquals(1, service.expireDraftOrders());
        }
        assertEquals(OrderStatus.DRAFT, failed.getStatus());
        assertEquals(OrderStatus.EXPIRED, successful.getStatus());
        verify(orders, never()).save(failed);
        verify(histories).save(any(OrderStatusHistory.class));
    }

    private Order draft() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.DRAFT);
        return order;
    }
}
