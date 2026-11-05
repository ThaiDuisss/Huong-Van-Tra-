package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.enums.OrderInventoryProcessingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderBackorderSupportTest {

    @Mock
    private InventoryClient inventoryClient;

    @Mock private OrderCustomBundleSupport bundles;

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

    @Test
    void stockPreparationIsDeferredUntilCommitAndNotSentForRolledBackTransaction() {
        Order order = order();
        order.addItem(OrderItem.builder().productVariantId(UUID.randomUUID()).quantity(new BigDecimal("2.5")).build());
        order.setInventoryFailureReason("old failure");
        TransactionSynchronizationManager.initSynchronization();
        try {
            support.enterWaitingStock(order);
            verifyNoInteractions(inventoryClient);
            assertNull(order.getInventoryFailureReason());
            assertEquals(OrderInventoryProcessingStatus.WAITING_STOCK, order.getInventoryProcessingStatus());
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            // The rollback callback does not dispatch prepareBackorder.
            callbacks.getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(inventoryClient);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        try {
            support.enterWaitingStock(order);
            verifyNoInteractions(inventoryClient);
            TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
            ArgumentCaptor<InventoryClient.AvailabilityEstimateRequest> request = ArgumentCaptor.forClass(InventoryClient.AvailabilityEstimateRequest.class);
            verify(inventoryClient).prepareBackorder(request.capture());
            assertEquals(order.getId(), request.getValue().orderId());
            assertEquals(new BigDecimal("2.5"), request.getValue().lines().getFirst().quantity());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,0,1", "true,false,0,2", "false,true,2,1", "true,true,1,2"})
    void mixedAvailabilityCombinesProductAndUnrequestedBundleWithoutCountingBundleVariant(
            boolean productMissing, boolean bundleEnough, int productDays, int bundleDays) {
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        Order order = order();
        UUID variant = UUID.randomUUID();
        order.addItem(OrderItem.builder().productVariantId(variant).quantity(new BigDecimal("1.5")).build());
        order.addItem(OrderItem.builder().productVariantId(variant).quantity(new BigDecimal("2.5")).build());
        order.addItem(OrderItem.builder().itemType(OrderItemType.CUSTOM_BUNDLE).quantity(BigDecimal.ONE).build());
        Instant productAt = productDays == 0 ? null : now.plusSeconds(productDays * 86400L);
        Instant bundleAt = now.plusSeconds(bundleDays * 86400L);
        when(inventoryClient.estimateAvailability(any())).thenReturn(
                new InventoryClient.AvailabilityEstimateResponse(productMissing, productAt, null));
        when(bundles.availability(order)).thenReturn(new OrderCustomBundleSupport.BundleAvailability(bundleEnough, bundleAt));
        var result = support.estimate(order);
        assertEquals(productMissing || !bundleEnough, result.backorder());
        assertEquals(!bundleEnough && (productAt == null || bundleAt.isAfter(productAt)) ? bundleAt : productAt, result.estimatedAvailableAt());
        assertTrue(result.lines().isEmpty());
        ArgumentCaptor<InventoryClient.AvailabilityEstimateRequest> request = ArgumentCaptor.forClass(InventoryClient.AvailabilityEstimateRequest.class);
        verify(inventoryClient).estimateAvailability(request.capture());
        assertEquals(1, request.getValue().lines().size());
        assertEquals(variant, request.getValue().lines().getFirst().productVariantId());
        assertEquals(new BigDecimal("4.0"), request.getValue().lines().getFirst().quantity());
    }

    @Test
    void bundleOnlyUsesPackingPathAndSkipsVariantEstimateOrPreparation() {
        Order order = order();
        OrderItem bundle = OrderItem.builder().itemType(OrderItemType.CUSTOM_BUNDLE).quantity(BigDecimal.ONE).build();
        order.addItem(bundle);
        when(bundles.availability(order)).thenReturn(new OrderCustomBundleSupport.BundleAvailability(true, null));
        assertFalse(support.estimate(order).backorder());
        assertFalse(support.prepare(order).backorder());
        support.enterWaitingStock(order);
        verify(bundles).requestPacking(order);
        verifyNoInteractions(inventoryClient);
        support.refreshBundles(order);
        support.cancelBundlePacking(order, "cancelled");
        support.requireBundlesPacked(order);
        verify(bundles).refreshPacking(order);
        verify(bundles).cancelPacking(order, "cancelled");
        verify(bundles).requireAllPacked(order);
        assertTrue(support.hasUnpackedBundle(order));
        when(bundles.allPacked(order)).thenReturn(true);
        assertFalse(support.hasUnpackedBundle(order));
    }

    @Test
    void alreadyRequestedBundleDoesNotReestimateMaterialsAndMissingProductEstimateRemainsUnknown() {
        Order order = order();
        OrderItem bundle = OrderItem.builder().itemType(OrderItemType.CUSTOM_BUNDLE).packingReferenceId(UUID.randomUUID()).build();
        order.addItem(bundle);
        assertFalse(support.estimate(order).backorder());
        verifyNoInteractions(bundles, inventoryClient);
        order.addItem(OrderItem.builder().productVariantId(UUID.randomUUID()).quantity(BigDecimal.ONE).build());
        assertNull(support.estimate(order));
        verify(inventoryClient).estimateAvailability(any());
        verifyNoInteractions(bundles);
        assertFalse(support.hasBundle(null));
        assertFalse(support.hasUnpackedBundle(null));
        assertFalse(support.isAwaitingStock(null));
    }

    @Test void emptyOrderBundleActionsAreNoOpsAndReservedInventoryIsNeverAwaitingStock() {
        Order order = order();
        support.refreshBundles(order);
        support.cancelBundlePacking(order, "cancel");
        support.requireBundlesPacked(order);
        assertFalse(support.hasUnpackedBundle(order));
        order.setBackorder(true);
        order.setInventoryProcessingStatus(OrderInventoryProcessingStatus.RESERVED);
        assertFalse(support.isAwaitingStock(order));
        verifyNoInteractions(bundles, inventoryClient);
    }

    @Test void prepareRequestSkipsMissingIdentityOrQuantityAndPreservesExactBranchAndOrder() {
        Order order = order();
        UUID branch = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        order.setBranchId(branch);
        order.addItem(OrderItem.builder().quantity(BigDecimal.TEN).build());
        order.addItem(OrderItem.builder().productVariantId(variant).quantity(null).build());
        order.addItem(OrderItem.builder().productVariantId(variant).quantity(new BigDecimal("0.25")).build());
        support.prepare(order);
        ArgumentCaptor<InventoryClient.AvailabilityEstimateRequest> request = ArgumentCaptor.forClass(InventoryClient.AvailabilityEstimateRequest.class);
        verify(inventoryClient).prepareBackorder(request.capture());
        assertEquals(branch, request.getValue().branchId());
        assertEquals(order.getId(), request.getValue().orderId());
        assertEquals(1, request.getValue().lines().size());
        assertEquals(variant, request.getValue().lines().getFirst().productVariantId());
        assertEquals(new BigDecimal("0.25"), request.getValue().lines().getFirst().quantity());
        verifyNoInteractions(bundles);
    }

    private Order order() {
        Order order = Order.builder().orderCode("ORD-001").items(new ArrayList<>()).build();
        order.setId(UUID.randomUUID());
        return order;
    }
}
