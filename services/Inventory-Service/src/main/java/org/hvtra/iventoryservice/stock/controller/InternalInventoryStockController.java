package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.StockBatchRequest;
import org.hvtra.iventoryservice.dto.request.StockVariantBatchRequest;
import org.hvtra.iventoryservice.dto.response.StockBatchResponse;
import org.hvtra.iventoryservice.dto.response.StockVariantBatchResponse;
import org.hvtra.iventoryservice.service.InventoryStockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/inventory/stocks")
public class InternalInventoryStockController {
    private final InventoryStockService service;

    public InternalInventoryStockController(InventoryStockService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    public List<StockBatchResponse> getStocksByProductIds(@Valid @RequestBody StockBatchRequest request) {
        return service.getStocksByProductIds(request);
    }

    @PostMapping("/variants/batch")
    public List<StockVariantBatchResponse> getStocksByVariantIds(
            @Valid @RequestBody StockVariantBatchRequest request) {
        return service.getStocksByVariantIds(request);
    }
}
