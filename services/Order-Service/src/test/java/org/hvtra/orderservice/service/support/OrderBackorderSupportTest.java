package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderBackorderSupportTest {

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderBackorderSupport support;

    @Test
    void awaitingStockOnlyForBackordersNotYetDeducted() {
        Order order = order();
        assertFalse(support.isAwaitingStock(order));

        order.setBackorder(true);
        assertTrue(support.isAwaitingStock(order));

        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.WAITING_STOCK);
        assertTrue(support.isAwaitingStock(order));

        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.CONSUMED);
        assertFalse(support.isAwaitingStock(order));
    }

    @Test
    void enterWaitingStockAsksInventoryToPrepareWithQuantitiesMergedPerVariant() {
        UUID variantId = UUID.randomUUID();
        Order order = order();
        order.setExpiresAt(Instant.now());
        order.addItem(OrderItem.builder().productVariantId(variantId).quantity(new BigDecimal("2")).build());
        order.addItem(OrderItem.builder().productVariantId(variantId).quantity(new BigDecimal("3")).build());

        support.enterWaitingStock(order);

        assertEquals(OrderInventoryProcessingStatus.WAITING_STOCK, order.getInventoryProcessingStatus());
        assertNull(order.getExpiresAt());
        ArgumentCaptor<InventoryClient.AvailabilityEstimateRequest> captor =
                ArgumentCaptor.forClass(InventoryClient.AvailabilityEstimateRequest.class);
        verify(inventoryClient).prepareBackorder(captor.capture());
        assertEquals(order.getId(), captor.getValue().orderId());
        assertEquals("ORD-001", captor.getValue().orderCode());
        assertEquals(1, captor.getValue().lines().size());
        assertEquals(new BigDecimal("5"), captor.getValue().lines().get(0).quantity());
    }

    @Test
    void inventoryFailureDoesNotBreakPayment() {
        Order order = order();
        when(inventoryClient.prepareBackorder(any())).thenThrow(new IllegalStateException("inventory down"));

        assertDoesNotThrow(() -> support.enterWaitingStock(order));
        assertEquals(OrderInventoryProcessingStatus.WAITING_STOCK, order.getInventoryProcessingStatus());
    }

    private Order order() {
        Order order = Order.builder().orderCode("ORD-001").items(new ArrayList<>()).build();
        order.setId(UUID.randomUUID());
        return order;
    }
}
