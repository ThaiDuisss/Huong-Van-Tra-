package org.hvtra.iventoryservice.stock.service.impl;

import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.item.repository.InventoryItemRepository;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.stock.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.stock.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.stock.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.stock.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.stock.repository.InventoryBalanceRepository;
import org.hvtra.iventoryservice.stock.service.InventoryStockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryStockServiceImplTest {

    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private InventoryStockServiceImpl service;

    private UUID productId;
    private UUID variantId;
    private UUID itemId;
    private UUID warehouseId;
    private InventoryItem item;

    @BeforeEach
    void setUp() {
        productId = UUID.fromString("550e8400-e29b-41d4-a716-446655440090");
        variantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440091");
        itemId = UUID.fromString("550e8400-e29b-41d4-a716-446655440092");
        warehouseId = UUID.fromString("550e8400-e29b-41d4-a716-446655440093");

        item = new InventoryItem();
        item.setId(itemId);
        item.setReferenceId(variantId);
        item.setItemType(InventoryItemType.PRODUCT_VARIANT);
        item.setStatus(InventoryStatus.ACTIVE);
    }

    @Test
    @DisplayName("getStocksByProductIds - Throws BadRequestException when request is empty")
    void getStocksByProductIds_EmptyRequest_ThrowsException() {
        StockBatchRequest request = new StockBatchRequest(Collections.emptyList(), warehouseId);

        assertThatThrownBy(() -> service.getStocksByProductIds(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("productIds không được để trống");
    }

    @Test
    @DisplayName("getStocksByProductIds - Success when no variant refs returned (OUT_OF_STOCK)")
    void getStocksByProductIds_NoVariants_OutOfStock() {
        StockBatchRequest request = new StockBatchRequest(List.of(productId), warehouseId);
        when(catalogClient.getVariantRefsByProductIds(List.of(productId))).thenReturn(Collections.emptyList());

        List<StockBatchResponse> result = service.getStocksByProductIds(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(result.getFirst().availableQuantity()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getStocksByProductIds - Success when catalog client has variants")
    void getStocksByProductIds_Success() {
        StockBatchRequest request = new StockBatchRequest(List.of(productId), warehouseId);
        CatalogClient.ProductVariantProductRefResponse ref =
                new CatalogClient.ProductVariantProductRefResponse(variantId, productId);
        when(catalogClient.getVariantRefsByProductIds(List.of(productId))).thenReturn(List.of(ref));
        when(itemRepository.findByReferenceIdInAndItemType(any(), any())).thenReturn(List.of(item));
        when(balanceRepository.findSaleableBalancesByInventoryItemIds(any(), any(), any())).thenReturn(Collections.emptyList());

        List<StockBatchResponse> result = service.getStocksByProductIds(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stockStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("getStocksByVariantIds - Throws BadRequestException when variantIds is empty")
    void getStocksByVariantIds_EmptyRequest_ThrowsException() {
        StockVariantBatchRequest request = new StockVariantBatchRequest(Collections.emptyList(), warehouseId);

        assertThatThrownBy(() -> service.getStocksByVariantIds(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("variantIds không được để trống");
    }

    @Test
    @DisplayName("getStocksByVariantIds - Success returning variant stock info")
    void getStocksByVariantIds_Success() {
        StockVariantBatchRequest request = new StockVariantBatchRequest(List.of(variantId), warehouseId);
        when(itemRepository.findByReferenceIdInAndItemType(any(), any())).thenReturn(List.of(item));
        when(balanceRepository.findSaleableBalancesByInventoryItemIds(any(), any(), any())).thenReturn(Collections.emptyList());

        List<StockVariantBatchResponse> result = service.getStocksByVariantIds(request);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().variantId()).isEqualTo(variantId);
        assertThat(result.getFirst().stockStatus()).isEqualTo("OUT_OF_STOCK");
    }
}
