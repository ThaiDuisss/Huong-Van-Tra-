package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.BarcodeCreateRequest;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.ProductVariantService;
import org.hvtra.catalog.service.BarcodeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "variantName", required = false) String variantName,
            @RequestParam(name = "barcode", required = false) String barcode,
            @RequestParam(name = "productId", required = false) UUID productId,
            @RequestParam(name = "status", required = false) Status status,
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        return service.findAll(sku, variantName, barcode, productId, status, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse.VariantResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
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
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> exportVariants(
            @RequestParam(name = "productId", required = false) UUID productId) {
        byte[] excelBytes = service.exportToExcel(productId);
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(excelBytes);
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = java.time.LocalDateTime.now().format(formatter);
        String rawFilename = "Danh sách biến thể sản phẩm_" + timestamp + ".xlsx";
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
    public void importVariants(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("productId") UUID productId) {
        service.importFromCsv(file, productId);
    }

    @GetMapping("/import/template")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        String template = "\uFEFFsep=,\n" +
                "ID,Tên biến thể,SKU,Giá bán,Giá vốn,Đơn vị,Chính sách tồn,Trạng thái\n" +
                ",Thiết Quan Âm 100g,TQA-100G,150000,100000,Gói,TRACK_STOCK,Đang hoạt động\n" +
                ",Thiết Quan Âm 250g,TQA-250G,350000,230000,Gói,TRACK_STOCK,Đang hoạt động\n";
        byte[] bytes = template.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_bien_the.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}
