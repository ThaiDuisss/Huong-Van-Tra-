package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.SellableConfigRequest;
import org.hvtra.catalog.dto.request.SellableQuantityAdjustmentRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.SellableQuantityAdjustmentResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;
import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;
import org.hvtra.catalog.dto.response.VariantCostResponse;
import org.hvtra.catalog.dto.response.VariantInventorySummaryResponse;

public interface ProductVariantService {
    PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String keyword, String sku, String variantName, String barcode, UUID productId, UUID categoryId, Status status, Pageable pageable);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);
    ProductResponse.VariantResponse findById(UUID id);
    ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request);
    ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request);
    ProductResponse.VariantResponse updateStatus(UUID id, Status status);
    ProductResponse.VariantResponse updateSellableConfig(UUID id, SellableConfigRequest request);
    SellableQuantityAdjustmentResponse decrementSellableQuantity(UUID id, SellableQuantityAdjustmentRequest request);
    SellableQuantityAdjustmentResponse restoreSellableQuantity(UUID id, SellableQuantityAdjustmentRequest request);
    byte[] exportToExcel(UUID productId);
    byte[] getImportTemplate();
    List<ProductResponse.VariantResponse> findAllByIds(List<UUID> ids);
    List<UUID> searchVariantIds(String keyword, String sku, String productCode, String productName, String categoryCode, UUID categoryId, UUID unitId);
    List<ProductVariantProductRefResponse> findRefsByProductIds(List<UUID> productIds);
    List<VariantCostResponse> findCostPricesByIds(List<UUID> ids);
    List<VariantInventorySummaryResponse> findInventorySummariesByIds(List<UUID> ids);
    void delete(UUID id);
}
