package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.BomLookupResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.BomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/boms")
public class InternalBomController {
    private final BomService service;

    public InternalBomController(BomService service) {
        this.service = service;
    }

    @GetMapping("/lookup")
    public List<BomLookupResponse> lookup(
            @RequestParam(name = "productVariantId") UUID productVariantId,
            @RequestParam(name = "status", required = false) Status status) {
        return service.lookupByVariant(productVariantId, status);
    }
}
