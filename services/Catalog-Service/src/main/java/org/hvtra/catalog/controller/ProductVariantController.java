package org.hvtra.catalog.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.catalog.dto.request.BarcodeCreateRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.service.BarcodeService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.hvtra.catalog.dto.response.ProductVariantSearchViewResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/catalog/product-variants")
public class ProductVariantController {
    private final ProductVariantService service;
    private final BarcodeService barcodeService;

    public ProductVariantController(ProductVariantService service, BarcodeService barcodeService) {
        this.service = service;
        this.barcodeService = barcodeService;
    }

    @GetMapping
    public PageResponse<ProductResponse.VariantSummaryResponse> findAll(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "variantName", required = false) String variantName,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return service.findAll(keyword, sku, variantName, barcode, productId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse.VariantResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public List<ProductResponse.VariantResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @GetMapping("/search-ids")
    public List<UUID> searchIds(
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productCode", required = false) String productCode,
            @RequestParam(name = "productName", required = false) String productName,
            @RequestParam(name = "categoryCode", required = false) String categoryCode,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "unitId", required = false) UUID unitId) {
        return service.searchVariantIds(sku, productCode, productName, categoryCode, categoryId, unitId);
    }

    @Tag(
            name = "API cho POS CatalogService",
            description = "Output: productId, variantId, sku, productName, variantName, categoryName, sellingPrice, imageUrl"
    )
    @GetMapping("/search-view-pos")
    public List<ProductVariantSearchViewResponse> searchVariants(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "productName", required = false) String productName) {
        log.info("value {}", service.searchVariants(keyword, sku, productName));
        return service.searchVariants(keyword, sku, productName);
    }

    @Tag(
            name = "API cho POS CatalogService",
            description = "Output: productId, variantId, sku, productName, variantName, categoryName, sellingPrice, imageUrl"
    )
    @GetMapping("/{variantId}/pos-view")
    public ProductVariantSearchViewResponse getPosView(
            @PathVariable("variantId") UUID variantId) {
        return service.getPosView(variantId);
    }

    @PutMapping("/{id}")
    public ProductResponse.VariantResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody ProductVariantRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse.VariantResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PostMapping("/{variantId}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse.BarcodeResponse createBarcode(
            @PathVariable("variantId") UUID variantId,
            @Valid @RequestBody BarcodeCreateRequest request) {
        return barcodeService.create(variantId, request);
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportVariants(
            @RequestParam(name = "productId", required = false) UUID productId) {
        byte[] excelBytes = service.exportToExcel(productId);
        ByteArrayResource resource = new ByteArrayResource(excelBytes);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String rawFilename = "Danh_sach_bien_the_san_pham_" + timestamp + ".xlsx";
        String encodedFilename = "";
        try {
            encodedFilename = URLEncoder.encode(rawFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public void importVariants(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productId") UUID productId) {
        service.importFromExcel(file, productId);
    }

    @GetMapping("/import/template")
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] excelBytes = service.getImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_bien_the.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
