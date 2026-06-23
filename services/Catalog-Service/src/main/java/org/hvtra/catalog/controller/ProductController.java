package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.ProductRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.ProductService;
import org.hvtra.catalog.service.ProductVariantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {
    private final ProductService service;
    private final ProductVariantService variantService;

    public ProductController(ProductService service, ProductVariantService variantService) {
        this.service = service;
        this.variantService = variantService;
    }

    @GetMapping
    public PageResponse<ProductSummaryResponse> findAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(name, categoryId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse.VariantResponse createVariant(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody ProductVariantRequest request) {
        return variantService.create(productId, request);
    }

    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> exportProducts() {
        byte[] excelBytes = service.exportToExcel();
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(excelBytes);
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = java.time.LocalDateTime.now().format(formatter);
        String rawFilename = "Danh sách sản phẩm_" + timestamp + ".xlsx";
        String encodedFilename = "";
        try {
            encodedFilename = java.net.URLEncoder.encode(rawFilename, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {}
        
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public void importProducts(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        service.importFromExcel(file);
    }

    @GetMapping("/import/template")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadTemplate() {
        byte[] excelBytes = service.getImportTemplate();
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(excelBytes);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_san_pham.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
