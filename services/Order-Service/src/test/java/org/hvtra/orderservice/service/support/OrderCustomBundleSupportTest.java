package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.CustomBundleCatalogClient;
import org.hvtra.orderservice.client.CustomBundleInventoryClient;
import org.hvtra.orderservice.domain.entity.Order;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.domain.entity.OrderItemComponent;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;
import org.hvtra.orderservice.dto.request.CustomBundleLineRequest;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCustomBundleSupportTest {
    @Mock private CustomBundleCatalogClient catalog;
    @Mock private CustomBundleInventoryClient inventory;
    private OrderCustomBundleSupport support;
    private final UUID ingredientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        support = new OrderCustomBundleSupport(catalog, inventory, new BigDecimal("50"), new BigDecimal("1000"),
                new BigDecimal("5"), 10, new BigDecimal("2.50"));
    }

    @Test
    void mergesIngredientLinesSnapshotsCatalogAndRoundsBeforeAddingPackaging() {
        when(catalog.getIngredientsByIds(List.of(ingredientId)))
                .thenReturn(List.of(ingredient("ACTIVE", 1, new BigDecimal("1.2345"))));
        var priced = support.price(List.of(line(ingredientId, "20"), line(ingredientId, "30")));
        assertEquals(1, priced.components().size());
        var component = priced.components().getFirst();
        assertEquals(ingredientId, component.getIngredientId());
        assertEquals(new BigDecimal("50"), component.getQuantity());
        assertEquals("g", component.getUnitSnapshot());
        assertEquals("TEA", component.getIngredientCodeSnapshot());
        assertEquals("Tea", component.getIngredientNameSnapshot());
        assertEquals(new BigDecimal("1.2345"), component.getUnitPrice());
        assertEquals(new BigDecimal("61.73"), component.getLineTotal());
        assertEquals(new BigDecimal("64"), priced.unitPrice());
        verifyNoInteractions(inventory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"50", "1000"})
    void acceptsInclusiveTotalQuantityBoundaries(String quantity) {
        when(catalog.getIngredientsByIds(List.of(ingredientId))).thenReturn(List.of(ingredient("ACTIVE", 1, BigDecimal.ONE)));
        assertEquals(new BigDecimal(quantity), support.price(List.of(line(ingredientId, quantity))).components().getFirst().getQuantity());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBundleWithoutIngredients(List<CustomBundleLineRequest> lines) {
        assertThrows(InvalidOrderStatusException.class, () -> support.price(lines));
        verifyNoInteractions(catalog, inventory);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "49.999", "1000.001"})
    void rejectsInvalidOrOutOfRangeQuantityBeforeCatalogCall(String quantity) {
        CustomBundleLineRequest line = line(ingredientId, quantity == null || quantity.isEmpty() ? null : quantity);
        assertThrows(InvalidOrderStatusException.class, () -> support.price(List.of(line)));
        verifyNoInteractions(catalog, inventory);
    }

    @Test
    void rejectsMissingIdentityOrTooManyDistinctIngredientsBeforeLookup() {
        assertThrows(InvalidOrderStatusException.class, () -> support.price(List.of(line(null, "50"))));
        var tooMany = IntStream.range(0, 11).mapToObj(i -> line(UUID.randomUUID(), "5")).toList();
        assertThrows(InvalidOrderStatusException.class, () -> support.price(tooMany));
        verifyNoInteractions(catalog, inventory);
    }

    @ParameterizedTest
    @CsvSource({"INACTIVE,1,1", "ACTIVE,0,1", "ACTIVE,,1", "ACTIVE,1,"})
    void rejectsIngredientThatIsNotRetailSaleable(String status, Integer type, BigDecimal price) {
        when(catalog.getIngredientsByIds(List.of(ingredientId))).thenReturn(List.of(ingredient(status, type, price)));
        assertThrows(InvalidOrderStatusException.class, () -> support.price(List.of(line(ingredientId, "50"))));
    }

    @Test
    void rejectsMissingCatalogIngredientAndLineBelowMinimumEvenWhenTotalIsValid() {
        UUID other = UUID.randomUUID();
        var lines = List.of(line(ingredientId, "4.99"), line(other, "50"));
        when(catalog.getIngredientsByIds(List.of(ingredientId, other))).thenReturn(List.of());
        assertThrows(InvalidOrderStatusException.class, () -> support.price(lines));
        when(catalog.getIngredientsByIds(List.of(ingredientId, other))).thenReturn(List.of(ingredient("ACTIVE", 1, BigDecimal.ONE)));
        assertThrows(InvalidOrderStatusException.class, () -> support.price(lines));
    }

    @Test
    void missingSystemVariantIsAConfigurationFailure() {
        assertThrows(InvalidOrderStatusException.class, support::customBundleVariant);
    }

    @Test
    void availabilitySkipsAlreadyRequestedPackingAndChoosesLatestShortageDate() {
        OrderItem first = bundle();
        OrderItem second = bundle();
        OrderItem requested = bundle();
        requested.setPackingReferenceId(UUID.randomUUID());
        Order order = order(first, second, requested);
        Instant earliest = Instant.parse("2026-11-03T01:00:00Z");
        Instant latest = earliest.plusSeconds(3600);
        when(inventory.checkAvailability(any())).thenReturn(
                new CustomBundleInventoryClient.AvailabilityResponse(false, earliest, 1),
                new CustomBundleInventoryClient.AvailabilityResponse(false, latest, 1));
        var result = support.availability(order);
        assertFalse(result.enough());
        assertEquals(latest, result.estimatedAvailableAt());
        verify(inventory, times(2)).checkAvailability(any());
    }

    @Test
    void packingRequestCarriesExactComponentsAndIsNotCreatedTwice() {
        OrderItem item = bundle();
        Order order = order(item);
        UUID packingId = UUID.randomUUID();
        Instant eta = Instant.parse("2026-11-04T00:00:00Z");
        when(inventory.createPacking(any())).thenReturn(new CustomBundleInventoryClient.PackingResponse(
                packingId, "PACK-1", order.getId(), item.getId(), "WAITING_MATERIAL", eta));
        support.requestPacking(order);
        support.requestPacking(order);
        ArgumentCaptor<CustomBundleInventoryClient.CreatePackingRequest> request =
                ArgumentCaptor.forClass(CustomBundleInventoryClient.CreatePackingRequest.class);
        verify(inventory).createPacking(request.capture());
        assertEquals(order.getId(), request.getValue().orderId());
        assertEquals(item.getId(), request.getValue().orderItemId());
        assertEquals(item.getProductVariantId(), request.getValue().productVariantId());
        assertEquals(order.getBranchId(), request.getValue().branchId());
        assertEquals(ingredientId, request.getValue().lines().getFirst().ingredientId());
        assertEquals(new BigDecimal("50"), request.getValue().lines().getFirst().quantity());
        assertEquals(packingId, item.getPackingReferenceId());
        assertEquals(BundlePackingStatus.WAITING_MATERIAL, item.getPackingStatus());
        assertEquals(eta, order.getExpectedAvailableAt());
    }

    @Test
    void packingFailureDoesNotUndoPaymentAndRefreshCanRecoverLater() {
        OrderItem item = bundle();
        Order order = order(item);
        when(inventory.createPacking(any())).thenThrow(new IllegalStateException("inventory down"));
        assertDoesNotThrow(() -> support.requestPacking(order));
        assertNull(item.getPackingReferenceId());
        when(inventory.findPackingsByOrder(order.getId())).thenReturn(List.of(new CustomBundleInventoryClient.PackingResponse(
                UUID.randomUUID(), "PACK-2", order.getId(), item.getId(), "PACKED", null)));
        support.refreshPacking(order);
        assertTrue(support.allPacked(order));
        assertDoesNotThrow(() -> support.requireAllPacked(order));
    }

    @Test
    void handoverRejectsUnpackedBundleAndPackedEventIsIdempotent() {
        OrderItem item = bundle();
        item.setVariantNameSnapshot("Custom tea");
        Order order = order(item);
        assertFalse(support.allPacked(order));
        assertThrows(InvalidOrderStatusException.class, () -> support.requireAllPacked(order));
        assertTrue(support.markPacked(item, "PACK-3"));
        assertFalse(support.markPacked(item, "OTHER"));
        assertEquals("PACK-3", item.getPackingCode());
        assertFalse(support.markPacked(new OrderItem(), "PACK-3"));
    }

    @Test
    void cancelPackingPropagatesFailureAndAppliesSuccessfulCancellation() {
        OrderItem item = bundle();
        Order order = order(item);
        support.cancelPacking(order, "customer cancelled");
        verifyNoInteractions(inventory);
        item.setPackingReferenceId(UUID.randomUUID());
        when(inventory.cancelPackingsByOrder(any())).thenThrow(new IllegalStateException("already packed"));
        assertThrows(InvalidOrderStatusException.class, () -> support.cancelPacking(order, "reason"));
        doReturn(List.of(new CustomBundleInventoryClient.PackingResponse(
                item.getPackingReferenceId(), "PACK-4", order.getId(), item.getId(), "CANCELLED", null)))
                .when(inventory).cancelPackingsByOrder(any());
        support.cancelPacking(order, "reason");
        assertEquals(BundlePackingStatus.CANCELLED, item.getPackingStatus());
    }

    @Test
    void ordersWithoutBundlesNeverCallInventory() {
        Order order = order(new OrderItem());
        assertFalse(support.hasBundle(order));
        assertTrue(support.allPacked(order));
        support.refreshPacking(order);
        support.requestPacking(order);
        assertTrue(support.availability(order).enough());
        order.setItems(null);
        assertFalse(support.hasBundle(order));
        assertTrue(support.bundleItems(order).isEmpty());
        verifyNoInteractions(inventory);
    }

    private CustomBundleCatalogClient.IngredientResponse ingredient(String status, Integer type, BigDecimal price) {
        return new CustomBundleCatalogClient.IngredientResponse(ingredientId, "TEA", "Tea", "g", type, status, price);
    }

    private CustomBundleLineRequest line(UUID id, String quantity) {
        var line = new CustomBundleLineRequest();
        line.setIngredientId(id);
        line.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        return line;
    }

    private OrderItem bundle() {
        var component = new OrderItemComponent();
        component.setIngredientId(ingredientId);
        component.setIngredientNameSnapshot("Tea");
        component.setQuantity(new BigDecimal("50"));
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setProductVariantId(UUID.randomUUID());
        item.setItemType(OrderItemType.CUSTOM_BUNDLE);
        item.setComponents(List.of(component));
        return item;
    }

    private Order order(OrderItem... items) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("UNIT-BUNDLE");
        order.setBranchId(UUID.randomUUID());
        order.setItems(List.of(items));
        return order;
    }
}
