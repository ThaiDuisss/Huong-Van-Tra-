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
        verify(catalog).findAll(null, null, null, null, null, null, "ACTIVE", 0, 20);
        verifyNoInteractions(inventory);
    }

    @Test
    void explicitProductFilterWithEmptyCatalogPageReturnsEmptyBeforePriceOrStockLookup() {
        UUID parent = UUID.fromString("22222222-2222-4222-8222-222222222222");
        when(catalog.findAll(null, null, null, null, parent, null, "ACTIVE", 0, 50))
                .thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0, true, true));

        var result = service.searchVariants(new PosProductSearchRequest(null, null, null, parent, 0, 50));

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        assertEquals(50, result.size());
        verify(catalog).findAll(null, null, null, null, parent, null, "ACTIVE", 0, 50);
        verifyNoInteractions(inventory);
    }

    @Test
    void productNameFallbackAndExplicitParentFilterAreSentWithoutChangingCatalogTotals() {
        CatalogVariantResponse variant = variant();
        var request = new PosProductSearchRequest(" ", "SKU", "Tea", variant.productId(), 2, 5);
        when(catalog.findAll("Tea", "SKU", null, null, variant.productId(), null, "ACTIVE", 2, 5))
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
        when(catalog.findAll("Tea", null, null, null, null, null, "ACTIVE", 0, 20))
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
    void catalogFailurePropagatesInsteadOfBeingMisreportedAsEmptyStock() {
        UUID id = UUID.randomUUID();
        RuntimeException failure = new IllegalStateException("catalog unavailable");
        when(catalog.findById(id)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.getPosView(id)));
        verifyNoInteractions(inventory);
    }

    private CatalogVariantResponse variant() {
        return new CatalogVariantResponse(UUID.randomUUID(), UUID.randomUUID(), "Tea", "TEA", "SKU", "100g", "Tea",
                new BigDecimal("10000"), new BigDecimal("5000"), "image", UUID.randomUUID(), "STOCK", "ACTIVE", "ACTIVE", true, new BigDecimal("99"));
    }
}
