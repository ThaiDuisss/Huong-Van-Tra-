package org.hvtra.iventoryservice.batch.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.iventoryservice.batch.service.InventoryBatchService;
import org.hvtra.iventoryservice.stock.dto.response.WarehouseBatchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/batches")
@RequiredArgsConstructor
public class InventoryBatchController {

    private final InventoryBatchService batchService;

    /**
     * Lấy danh sách batch của một item tại một location (sắp xếp FEFO)
     * GET /api/inventory/batches?itemId=...&locationId=...
     */
    @GetMapping
    public ResponseEntity<List<WarehouseBatchResponse>> getBatches(
        @RequestParam UUID itemId,
        @RequestParam UUID locationId
    ) {
        var batches = batchService.getBatchesByItemAndLocation(itemId, locationId);
        return ResponseEntity.ok(batches);
    }

    /**
     * Lấy chi tiết batch tại một location
     * GET /api/inventory/batches/{batchId}?locationId=...
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<WarehouseBatchResponse> getBatchDetail(
        @PathVariable UUID batchId,
        @RequestParam UUID locationId
    ) {
        var batch = batchService.getBatchDetail(batchId, locationId);
        return ResponseEntity.ok(batch);
    }
}
