package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.SupplierResponse;
import org.hvtra.catalog.service.SupplierService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/suppliers")
public class InternalSupplierController {
    private final SupplierService service;

    public InternalSupplierController(SupplierService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    public List<SupplierResponse> getSuppliersBatch(@RequestBody List<UUID> ids) {
        return service.findByIds(ids);
    }
}
