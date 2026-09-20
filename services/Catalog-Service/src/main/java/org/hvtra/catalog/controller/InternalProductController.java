package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.service.ProductService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/products")
public class InternalProductController {
    private final ProductService service;

    public InternalProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    public List<ProductResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }
}
