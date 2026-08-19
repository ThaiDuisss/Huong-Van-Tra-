package org.hvtra.pos.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.pos.client.CatalogClient;
import org.hvtra.pos.client.InventoryClient;
import org.hvtra.pos.client.InventoryClient.StockVariantBatchRequest;
import org.hvtra.pos.client.InventoryClient.StockVariantBatchResponse;
import org.hvtra.pos.dto.request.PosProductSearchRequest;
import org.hvtra.pos.dto.response.CatalogVariantResponse;
import org.hvtra.pos.dto.response.PageResponse;
import org.hvtra.pos.dto.response.PosProductVariantResponse;
import org.hvtra.pos.service.PosProductService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PosProductServiceImpl implements PosProductService {

    private final CatalogClient catalogClient;
    private final InventoryClient inventoryClient;

    public PosProductServiceImpl(CatalogClient catalogClient, InventoryClient inventoryClient) {
        this.catalogClient = catalogClient;
        this.inventoryClient = inventoryClient;
    }

    @Override
    public List<PosProductVariantResponse> searchVariants(PosProductSearchRequest request) {
        String keyword = request != null ? request.keyword() : null;
        String sku = request != null ? request.sku() : null;
        String productName = request != null ? request.productName() : null;

        log.debug("POS searching variants with keyword: {}, sku: {}, productName: {}", keyword, sku, productName);

        String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword : productName;

        PageResponse<CatalogVariantResponse> pageResponse = catalogClient.findAll(
                searchKeyword,
                sku,
                null,
                null,
                null,
                null,
                "ACTIVE",
                0,
                100
        );

        if (pageResponse == null || pageResponse.content() == null || pageResponse.content().isEmpty()) {
            return Collections.emptyList();
        }

        List<CatalogVariantResponse> variants = pageResponse.content();
        List<UUID> variantIds = variants.stream()
                .map(CatalogVariantResponse::id)
                .distinct()
                .toList();

        Map<UUID, StockVariantBatchResponse> stockMap = loadStockMap(variantIds);

        return variants.stream()
                .map(v -> toPosProductVariantResponse(v, stockMap.get(v.id())))
                .toList();
    }

    @Override
    public PosProductVariantResponse getPosView(UUID variantId) {
        log.debug("POS fetching variant detail for POS view with variantId: {}", variantId);

        CatalogVariantResponse variant = catalogClient.findById(variantId);
        if (variant == null) {
            return null;
        }

        Map<UUID, StockVariantBatchResponse> stockMap = loadStockMap(List.of(variantId));
        StockVariantBatchResponse stock = stockMap.get(variantId);

        return toPosProductVariantResponse(variant, stock);
    }

    private Map<UUID, StockVariantBatchResponse> loadStockMap(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<StockVariantBatchResponse> responses = inventoryClient.getStocksByVariantIds(
                    new StockVariantBatchRequest(variantIds)
            );
            if (responses == null) {
                return Collections.emptyMap();
            }
            return responses.stream()
                    .collect(Collectors.toMap(
                            StockVariantBatchResponse::variantId,
                            Function.identity(),
                            (existing, replacement) -> existing
                    ));
        } catch (Exception ex) {
            log.warn("Failed to fetch stock from inventory service for variants {}: {}", variantIds, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private PosProductVariantResponse toPosProductVariantResponse(
            CatalogVariantResponse variant,
            StockVariantBatchResponse stock) {
        return PosProductVariantResponse.builder()
                .productId(variant.productId())
                .variantId(variant.id())
                .sku(variant.sku())
                .productName(variant.productName())
                .variantName(variant.variantName())
                .categoryName(variant.categoryName())
                .sellingPrice(variant.sellingPrice())
                .imageUrl(variant.imageUrl())
                .inventoryLocationId(stock != null ? stock.inventoryLocationId() : null)
                .availableQuantity(stock != null ? stock.availableQuantity() : BigDecimal.ZERO)
                .reservedQuantity(stock != null ? stock.reservedQuantity() : BigDecimal.ZERO)
                .stockStatus(stock != null ? stock.stockStatus() : "OUT_OF_STOCK")
                .build();
    }
}
