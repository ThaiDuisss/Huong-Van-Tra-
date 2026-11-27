package org.hvtra.orderservice.service.impl;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.dto.request.PosProductSearchRequest;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosProductServiceImplTest {
    @Mock private CatalogClient catalog;
    @Mock private InventoryClient inventory;
    @InjectMocks private PosProductServiceImpl service;

    @Test
    void nullSearchUsesDefaultActiveCatalogPageAndDoesNotFetchStockForEmptyResults() {
        var result = service.searchVariants(null);
        assertTrue(result.content().isEmpty());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        verify(catalog).findSellableVariants(null, null, null, true, 0, 20);
        verifyNoInteractions(inventory);
    }

    @Test
    void explicitProductFilterWithEmptyCatalogPageReturnsEmptyBeforePriceOrStockLookup() {
        UUID parent = UUID.fromString("22222222-2222-4222-8222-222222222222");
        when(catalog.findSellableVariants(null, null, parent, true, 0, 50))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, true, true));

        var result = service.searchVariants(new PosProductSearchRequest(null, null, null, parent, 0, 50));

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(50, result.size());
        verify(catalog).findSellableVariants(null, null, parent, true, 0, 50);
        verifyNoInteractions(inventory);
    }

    @Test
    void productNameFallbackAndExplicitParentFilterAreSentWithoutChangingCatalogTotals() {
        CatalogVariantResponse variant = variant();
        var request = new PosProductSearchRequest(" ", "SKU", "Tea", variant.productId(), 2, 5);
        when(catalog.findSellableVariants("Tea", "SKU", variant.productId(), true, 2, 5))
                .thenReturn(new PageResponse<>(List.of(variant), 2, 5, 11, 3, false, true));
        var result = service.searchVariants(request);
        assertEquals(11, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(2, result.page());
        assertEquals(variant.productId(), result.content().getFirst().productId());
        assertEquals(variant.id(), result.content().getFirst().variantId());
        assertEquals(variant.sellingPrice(), result.content().getFirst().sellingPrice());
    }

    @Test
    void stockLookupDeduplicatesVariantIdsAndKeepsFirstResponseForDuplicateIdentity() {
        CatalogVariantResponse variant = variant();
        when(catalog.findSellableVariants("Tea", null, null, true, 0, 20))
                .thenReturn(new PageResponse<>(List.of(variant, variant), 0, 20, 2, 1, true, true));
        UUID shelf = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        var stock = new InventoryClient.StockVariantBatchResponse(variant.id(), shelf, new BigDecimal("8"), BigDecimal.ONE,
                "IN_STOCK", new BigDecimal("3"), new BigDecimal("5"), shelf, warehouse);
        when(inventory.getStocksByVariantIds(any())).thenReturn(List.of(stock,
                new InventoryClient.StockVariantBatchResponse(variant.id(), null, BigDecimal.ZERO, BigDecimal.ZERO,
                        "OUT_OF_STOCK", null, null, null, null)));
        var result = service.searchVariants(new PosProductSearchRequest("Tea", null, null, null, 0, 0));
        ArgumentCaptor<InventoryClient.StockVariantBatchRequest> request = ArgumentCaptor.forClass(InventoryClient.StockVariantBatchRequest.class);
        verify(inventory).getStocksByVariantIds(request.capture());
        assertEquals(List.of(variant.id()), request.getValue().variantIds());
        assertNull(request.getValue().warehouseId());
        var row = result.content().getFirst();
        assertEquals(new BigDecimal("8"), row.availableQuantity());
        assertEquals(new BigDecimal("3"), row.shelfQuantity());
        assertEquals(new BigDecimal("5"), row.warehouseQuantity());
        assertEquals(shelf, row.shelfLocationId());
        assertEquals(warehouse, row.warehouseLocationId());
        assertEquals("IN_STOCK", row.stockStatus());
        assertEquals(new BigDecimal("99"), row.sellableQuantity());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingStockResponseOrInventoryFailureKeepsCatalogMetadataButUsesZeroFallback(boolean unavailable) {
        CatalogVariantResponse variant = variant();
        when(catalog.findById(variant.id())).thenReturn(variant);
        if (unavailable) {
            when(inventory.getStocksByVariantIds(any())).thenThrow(new IllegalStateException("inventory unavailable"));
        }
        var row = service.getPosView(variant.id());
        assertEquals(variant.id(), row.variantId());
        assertEquals(variant.sellingPrice(), row.sellingPrice());
        assertEquals(BigDecimal.ZERO, row.availableQuantity());
        assertEquals(BigDecimal.ZERO, row.shelfQuantity());
        assertEquals(BigDecimal.ZERO, row.warehouseQuantity());
        assertNull(row.inventoryLocationId());
        assertEquals("OUT_OF_STOCK", row.stockStatus());
        // This characterizes the existing fallback; it does not establish actual out-of-stock inventory.
    }

    @Test
    void nullableShelfAndWarehouseDoNotReplaceAuthoritativeAvailableQuantity() {
        CatalogVariantResponse variant = variant();
        when(catalog.findById(variant.id())).thenReturn(variant);
        when(inventory.getStocksByVariantIds(any())).thenReturn(List.of(new InventoryClient.StockVariantBatchResponse(
                variant.id(), null, BigDecimal.TEN, BigDecimal.ZERO, "IN_STOCK", null, null, null, null)));
        var row = service.getPosView(variant.id());
        assertEquals(BigDecimal.TEN, row.availableQuantity());
        assertEquals(BigDecimal.ZERO, row.shelfQuantity());
        assertEquals(BigDecimal.ZERO, row.warehouseQuantity());
    }

    @Test
    void missingVariantReturnsNullWithoutRequestingInventory() {
        assertNull(service.getPosView(UUID.randomUUID()));
        verifyNoInteractions(inventory);
    }

    @Test
    void inactiveProductVariantAndBundlePackagingAreNotReturnedAsPosProducts() {
        CatalogVariantResponse inactiveProduct = variant("SKU", "ACTIVE", "INACTIVE", false);
        CatalogVariantResponse inactiveVariant = variant("SKU", "INACTIVE", "ACTIVE", false);
        CatalogVariantResponse bundle = variant("CUSTOM-BUNDLE", "ACTIVE", "ACTIVE", true);
        when(catalog.findSellableVariants(null, null, null, true, 0, 20))
                .thenReturn(new PageResponse<>(List.of(inactiveProduct, inactiveVariant, bundle), 0, 20, 3, 1, true, true));
        for (CatalogVariantResponse variant : List.of(inactiveProduct, inactiveVariant, bundle)) {
            when(catalog.findById(variant.id())).thenReturn(variant);
            assertNull(service.getPosView(variant.id()));
        }

        assertTrue(service.searchVariants(null).content().isEmpty());
        verifyNoInteractions(inventory);
    }

    @Test
    void catalogFailurePropagatesInsteadOfBeingMisreportedAsEmptyStock() {
        UUID id = UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("catalog unavailable");
        when(catalog.findById(id)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.getPosView(id)));
        verifyNoInteractions(inventory);
    }

    @Test
    void customerBundleBomConvertsGramsToIngredientRetailKilograms() {
        UUID variantId = UUID.randomUUID();
        CatalogVariantResponse customerVariant = new CatalogVariantResponse(
                variantId, UUID.randomUUID(), "Trà Quýt", "KH-01", "TQ-00001", "Trà Quýt Stevia",
                "Trà khách tự chọn", new BigDecimal("92000"), BigDecimal.ZERO, null, UUID.randomUUID(),
                "DO_NOT_TRACK", "ACTIVE", "ACTIVE", true, BigDecimal.ZERO, "CUSTOMER");
        when(catalog.findById(variantId)).thenReturn(customerVariant);

        UUID ingGramId = UUID.randomUUID();
        UUID ingKgId = UUID.randomUUID();
        when(catalog.getBomItemsByVariantId(variantId)).thenReturn(List.of(
                new CatalogClient.BomItemResponse(
                        UUID.randomUUID(),
                        new CatalogClient.BomIngredientResponse(ingGramId, "VQK-G-59", "Vỏ quýt khô", UUID.randomUUID(), "Gram", "g"),
                        new BigDecimal("35.00"),
                        new CatalogClient.BomUnitResponse(UUID.randomUUID(), "Gram", "g", "WEIGHT", null, BigDecimal.ONE),
                        BigDecimal.ZERO),
                new CatalogClient.BomItemResponse(
                        UUID.randomUUID(),
                        new CatalogClient.BomIngredientResponse(ingKgId, "ING-CO-NGOT", "Cỏ ngọt", UUID.randomUUID(), "Kilogram", "kg"),
                        new BigDecimal("15.00"),
                        new CatalogClient.BomUnitResponse(UUID.randomUUID(), "Gram", "g", "WEIGHT", null, BigDecimal.ONE),
                        BigDecimal.ZERO)
        ));

        var bom = service.getCustomerBundleBom(variantId);

        assertEquals("CUSTOMER", bom.productType());
        assertEquals(2, bom.lines().size());
        assertEquals(0, new BigDecimal("35.00").compareTo(bom.lines().get(0).quantity()));
        assertEquals("g", bom.lines().get(0).unitSymbol());
        assertEquals(0, new BigDecimal("0.015").compareTo(bom.lines().get(1).quantity()));
        assertEquals("kg", bom.lines().get(1).unitSymbol());
    }

    private CatalogVariantResponse variant() {
        return variant("SKU", "ACTIVE", "ACTIVE", true);
    }

    private CatalogVariantResponse variant(String sku, String status, String productStatus, boolean sellable) {
        return new CatalogVariantResponse(UUID.randomUUID(), UUID.randomUUID(), "Tea", "TEA", sku, "100g", "Tea",
                new BigDecimal("10000"), new BigDecimal("5000"), "image", UUID.randomUUID(), "STOCK", status,
                productStatus, sellable, new BigDecimal("99"), "FINISHED_PRODUCT");
    }
}
