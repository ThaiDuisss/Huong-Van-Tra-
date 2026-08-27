package org.hvtra.catalog.service;

import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;
import org.hvtra.catalog.dto.response.ProductVariantProductRefResponse;

public interface ProductVariantService {
    PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            String keyword, String sku, String variantName, String barcode, UUID productId, UUID categoryId, Status status, Pageable pageable);
    PageResponse<LookupResponse> lookup(String search, Pageable pageable);
    ProductResponse.VariantResponse findById(UUID id);
    ProductResponse.VariantResponse create(UUID productId, ProductVariantRequest request);
    ProductResponse.VariantResponse update(UUID id, ProductVariantRequest request);
    ProductResponse.VariantResponse updateStatus(UUID id, Status status);
    byte[] exportToExcel(UUID productId);
    void importFromExcel(org.springframework.web.multipart.MultipartFile file, UUID productId);
    byte[] getImportTemplate();
    List<ProductResponse.VariantResponse> findAllByIds(List<UUID> ids);
    List<UUID> searchVariantIds(String keyword, String sku, String productCode, String productName, String categoryCode, UUID categoryId, UUID unitId);
    List<ProductVariantProductRefResponse> findRefsByProductIds(List<UUID> productIds);
    void delete(UUID id);
}
