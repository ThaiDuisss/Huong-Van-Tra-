package org.hvtra.orderservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchRequest;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchResponse;
import org.hvtra.orderservice.dto.request.PosProductSearchRequest;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.hvtra.orderservice.dto.response.PosProductVariantResponse;
import org.hvtra.orderservice.service.PosProductService;
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
    public PageResponse<PosProductVariantResponse> searchVariants(PosProductSearchRequest request) {
        String keyword = request != null ? request.keyword() : null;
        String sku = request != null ? request.sku() : null;
        String productName = request != null ? request.productName() : null;
        UUID productId = request != null ? request.productId() : null;
        int page = request != null ? request.page() : 0;
        int size = (request != null && request.size() > 0) ? request.size() : 20;

        log.debug("POS searching variants with keyword: {}, sku: {}, productName: {}, page: {}, size: {}",
                keyword, sku, productName, page, size);

        String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword : productName;

        PageResponse<CatalogVariantResponse> pageResponse = catalogClient.findAll(
                searchKeyword,
                sku,
                null,
                null,
                productId,
                null,
                "ACTIVE",
                page,
                size
        );

        if (pageResponse == null || pageResponse.content() == null || pageResponse.content().isEmpty()) {
            return new PageResponse<>(
                    Collections.emptyList(),
                    page,
                    size,
                    0,
                    0,
                    true,
                    true
            );
        }

        List<CatalogVariantResponse> variants = pageResponse.content();
        List<UUID> variantIds = variants.stream()
                .map(CatalogVariantResponse::id)
                .distinct()
                .toList();

        Map<UUID, StockVariantBatchResponse> stockMap = loadStockMap(variantIds);

        List<PosProductVariantResponse> posVariants = variants.stream()
                .map(v -> toPosProductVariantResponse(v, stockMap.get(v.id())))
                .toList();

        return new PageResponse<>(
                posVariants,
                pageResponse.page(),
                pageResponse.size(),
                pageResponse.totalElements(),
                pageResponse.totalPages(),
                pageResponse.first(),
                pageResponse.last()
        );
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
                .sellableQuantity(variant.sellableQuantity())
                .availableQuantity(stock != null ? stock.availableQuantity() : BigDecimal.ZERO)
                .reservedQuantity(stock != null ? stock.reservedQuantity() : BigDecimal.ZERO)
                .stockStatus(stock != null ? stock.stockStatus() : "OUT_OF_STOCK")
                .shelfQuantity(stock != null && stock.shelfQuantity() != null ? stock.shelfQuantity() : BigDecimal.ZERO)
                .warehouseQuantity(stock != null && stock.warehouseQuantity() != null
                        ? stock.warehouseQuantity() : BigDecimal.ZERO)
                .shelfLocationId(stock != null ? stock.shelfLocationId() : null)
                .warehouseLocationId(stock != null ? stock.warehouseLocationId() : null)
                .build();
    }
}
