package org.hvtra.orderservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchRequest;
import org.hvtra.orderservice.client.InventoryClient.StockVariantBatchResponse;
import org.hvtra.orderservice.dto.request.PosProductSearchRequest;
import org.hvtra.orderservice.dto.response.CatalogVariantResponse;
import org.hvtra.orderservice.dto.response.PageResponse;
import org.hvtra.orderservice.dto.response.PosProductVariantBomResponse;
import org.hvtra.orderservice.dto.response.PosProductVariantBomResponse.PosBundleBomLineResponse;
import org.hvtra.orderservice.dto.response.PosProductVariantResponse;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.exception.OrderNotFoundException;
import org.hvtra.orderservice.service.PosProductService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        PageResponse<CatalogVariantResponse> pageResponse = catalogClient.findSellableVariants(
                searchKeyword,
                sku,
                productId,
                true,
                page,
                size
        );

        if (pageResponse == null || pageResponse.content() == null) {
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

        List<CatalogVariantResponse> variants = pageResponse.content().stream()
                .filter(PosProductServiceImpl::isPosSaleVariant)
                .toList();
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
        if (!isPosSaleVariant(variant) || !variantId.equals(variant.id())) {
            return null;
        }

        Map<UUID, StockVariantBatchResponse> stockMap = loadStockMap(List.of(variantId));
        StockVariantBatchResponse stock = stockMap.get(variantId);

        return toPosProductVariantResponse(variant, stock);
    }

    @Override
    public PosProductVariantBomResponse getCustomerBundleBom(UUID variantId) {
        CatalogVariantResponse variant = catalogClient.findById(variantId);
        if (!isPosSaleVariant(variant) || !variantId.equals(variant.id())) {
            throw new OrderNotFoundException("Không tìm thấy biến thể sản phẩm bán hàng: " + variantId);
        }
        if (!"CUSTOMER".equalsIgnoreCase(variant.productType())) {
            throw new InvalidOrderStatusException("Biến thể này không thuộc loại công thức khách hàng (CUSTOMER)");
        }
        List<CatalogClient.BomItemResponse> bomItems = catalogClient.getBomItemsByVariantId(variantId);
        if (bomItems == null || bomItems.isEmpty()) {
            throw new InvalidOrderStatusException("Sản phẩm công thức khách hàng chưa có định mức nguyên liệu (BOM)");
        }
        List<PosBundleBomLineResponse> lines = bomItems.stream()
                .filter(item -> item != null && item.ingredient() != null && item.ingredient().id() != null
                        && item.quantity() != null && item.quantity().signum() > 0)
                .map(item -> {
                    CatalogClient.BomIngredientResponse ingredient = item.ingredient();
                    String targetUnit = ingredient.unitSymbol() != null && !ingredient.unitSymbol().isBlank()
                            ? ingredient.unitSymbol()
                            : (item.unit() != null ? item.unit().symbol() : null);
                    BigDecimal convertedQty = convertToIngredientUnit(item.quantity(), item.unit(), targetUnit);
                    return new PosBundleBomLineResponse(
                            ingredient.id(),
                            ingredient.ingredientCode(),
                            ingredient.name(),
                            targetUnit,
                            convertedQty);
                })
                .toList();
        if (lines.isEmpty()) {
            throw new InvalidOrderStatusException("Sản phẩm công thức khách hàng chưa có nguyên liệu hợp lệ");
        }
        return new PosProductVariantBomResponse(
                variant.id(),
                variant.sku(),
                variant.productName(),
                variant.variantName(),
                variant.productType(),
                lines);
    }

    private static BigDecimal convertToIngredientUnit(
            BigDecimal quantity,
            CatalogClient.BomUnitResponse sourceUnit,
            String targetUnitSymbol) {
        if (sourceUnit == null || sourceUnit.symbol() == null || targetUnitSymbol == null) {
            return quantity;
        }
        String src = sourceUnit.symbol().trim().toLowerCase();
        String dst = targetUnitSymbol.trim().toLowerCase();
        if (src.equals(dst)) {
            return quantity;
        }
        if ("g".equals(src) && "kg".equals(dst)) {
            return quantity.divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
        }
        if ("kg".equals(src) && "g".equals(dst)) {
            return quantity.multiply(new BigDecimal("1000")).setScale(3, RoundingMode.HALF_UP);
        }
        return quantity;
    }

    private static boolean isPosSaleVariant(CatalogVariantResponse variant) {
        return variant != null && variant.sellable() && !"CUSTOM-BUNDLE".equals(variant.sku());
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
                .productType(variant.productType() != null ? variant.productType() : "FINISHED_PRODUCT")
                .build();
    }
}
