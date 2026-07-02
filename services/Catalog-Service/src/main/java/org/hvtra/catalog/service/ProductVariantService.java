package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductVariantService {
    PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String keyword, String sku, String variantName, String barcode, UUID productId, Status status, Pageable pageable);
    ProductResponse.VariantResponse findById(UUID id);
    ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request);
    ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request);
    ProductResponse.VariantResponse updateStatus(UUID id, Status status);
    byte[] exportToExcel(UUID productId);
    void importFromExcel(org.springframework.web.multipart.MultipartFile file, UUID productId);
    byte[] getImportTemplate();
    java.util.List<ProductResponse.VariantResponse> findAllByIds(java.util.List<UUID> ids);
    java.util.List<UUID> searchVariantIds(String sku, String productCode, String productName, String categoryCode, UUID categoryId, UUID unitId);
    java.util.List<org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse> searchVariants(String keyword, String sku, String productName);
    org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse getPosView(UUID variantId);
    java.util.List<org.hvtra.catalog.dto.response.ProductVariantProductRefResponse> findRefsByProductIds(java.util.List<UUID> productIds);
}
