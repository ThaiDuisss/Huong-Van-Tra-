package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.BarcodeUpdateRequest;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.dto.response.ProductResponse;
import org.hvtra.iventoryservice.service.BarcodeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/barcodes")
public class BarcodeController {
    private final BarcodeService service;

    public BarcodeController(BarcodeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse.BarcodeResponse> findAll(
            @RequestParam(required = false) String value,
            @PageableDefault(size = 20, sort = "barcodeValue") Pageable pageable) {
        return service.findAll(value, pageable);
    }

    @GetMapping("/{value}")
    public ProductResponse.VariantResponse scan(@PathVariable String value) {
        return service.scan(value);
    }

    @PutMapping("/{id}")
    public ProductResponse.BarcodeResponse update(
            @PathVariable Long id, @Valid @RequestBody BarcodeUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
