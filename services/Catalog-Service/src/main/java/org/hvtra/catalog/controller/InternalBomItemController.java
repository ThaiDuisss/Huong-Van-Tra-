package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.service.BomItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/product-variants/{variantId}/bom-items")
public class InternalBomItemController {
    private final BomItemService service;

    public InternalBomItemController(BomItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<BomItemResponse> findByVariant(@PathVariable("variantId") UUID variantId) {
        return service.findByVariant(variantId);
    }
}
