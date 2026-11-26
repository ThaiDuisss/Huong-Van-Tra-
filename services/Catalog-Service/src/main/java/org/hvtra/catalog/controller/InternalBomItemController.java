package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.service.BomItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog")
public class InternalBomItemController {
    private final BomItemService service;

    public InternalBomItemController(BomItemService service) {
        this.service = service;
    }

    @GetMapping("/product-variants/{variantId}/bom-items")
    public List<BomItemResponse> findByVariant(@PathVariable("variantId") UUID variantId) {
        return service.findByVariant(variantId);
    }

    @PostMapping("/bom-items/batch")
    public Map<UUID, List<BomItemResponse>> findByVariantIds(@RequestBody List<UUID> variantIds) {
        return service.findByVariantIds(variantIds);
    }
}
