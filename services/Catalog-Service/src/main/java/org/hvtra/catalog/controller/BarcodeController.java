package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.BarcodeUpdateRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.service.BarcodeService;
import org.hvtra.commonservice.logging.action.UserAction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/barcodes")
public class BarcodeController {
    private final BarcodeService service;

    public BarcodeController(BarcodeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse.BarcodeResponse> findAll(
            @RequestParam(name = "value", required = false) String value,
            @PageableDefault(size = 20, sort = "barcodeValue") Pageable pageable) {
        return service.findAll(value, pageable);
    }

    @GetMapping("/{value}")
    public ProductResponse.VariantResponse scan(@PathVariable("value") String value) {
        return service.scan(value);
    }

    @UserAction(value = "BARCODE_UPDATE", entityType = "Barcode", entityId = "#id")
    @PutMapping("/{id}")
    public ProductResponse.BarcodeResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody BarcodeUpdateRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "BARCODE_DELETE", entityType = "Barcode", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}

