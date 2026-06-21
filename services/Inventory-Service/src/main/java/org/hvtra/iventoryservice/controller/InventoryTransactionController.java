package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.StockAdjustmentRequest;
import org.hvtra.iventoryservice.dto.request.StockInRequest;
import org.hvtra.iventoryservice.dto.response.InventoryTransactionResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.enums.InventoryTransactionType;
import org.hvtra.iventoryservice.service.InventoryTransactionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/inventory/inventory-transactions")
public class InventoryTransactionController {
    private final InventoryTransactionService service;

    public InventoryTransactionController(InventoryTransactionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryTransactionResponse> findAll(
            @RequestParam(name = "transactionCode", required = false) String transactionCode,
            @RequestParam(name = "type", required = false) InventoryTransactionType type,
            @RequestParam(name = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(name = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(name = "createdBy", required = false) Long createdBy,
            @RequestParam(name = "branchId", required = false) Long branchId,
            @RequestParam(name = "referenceType", required = false)
            InventoryReferenceType referenceType,
            @RequestParam(name = "referenceId", required = false) Long referenceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return service.findAll(
                transactionCode,
                type,
                fromDate,
                toDate,
                createdBy,
                branchId,
                referenceType,
                referenceId,
                pageable);
    }

    @GetMapping("/{id}")
    public InventoryTransactionResponse findById(@PathVariable("id") Long id) {
        return service.findById(id);
    }

    @PostMapping("/stock-in")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransactionResponse stockIn(@Valid @RequestBody StockInRequest request) {
        return service.stockIn(request);
    }

    @PostMapping("/adjustment")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTransactionResponse adjust(
            @Valid @RequestBody StockAdjustmentRequest request) {
        return service.adjust(request);
    }
}
