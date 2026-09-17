package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.BomItemRequest;
import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.service.BomItemService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/catalog/product-variants/{variantId}/bom-items")
public class BomItemController {
    private final BomItemService service;

    public BomItemController(BomItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<BomItemResponse> findByVariant(@PathVariable("variantId") UUID variantId) {
        return service.findByVariant(variantId);
    }

    @PutMapping
    public List<BomItemResponse> replaceForVariant(
            @PathVariable("variantId") UUID variantId,
            @RequestBody List<@Valid BomItemRequest> items) {
        return service.replaceForVariant(variantId, items);
    }
}
