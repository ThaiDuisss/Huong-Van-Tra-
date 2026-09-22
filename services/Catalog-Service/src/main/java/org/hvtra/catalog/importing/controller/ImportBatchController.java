package org.hvtra.catalog.importing.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.commonservice.common_importing.enums.ImportBatchStatus;
import org.hvtra.commonservice.common_importing.enums.ImportType;
import org.hvtra.commonservice.common_importing.dto.request.ApproveImportBatchRequest;
import org.hvtra.commonservice.common_importing.dto.request.RejectImportBatchRequest;
import org.hvtra.commonservice.common_importing.dto.request.SaveImportDraftRequest;
import org.hvtra.commonservice.common_importing.dto.request.SubmitImportBatchRequest;
import org.hvtra.commonservice.common_importing.dto.request.UpdateImportItemRequest;
import org.hvtra.commonservice.common_importing.dto.response.ImportBatchResponse;
import org.hvtra.commonservice.common_importing.dto.response.ImportPreviewResponse;
import org.hvtra.commonservice.common_importing.service.ImportBatchService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/import-batches")
public class ImportBatchController {

    private final ImportBatchService service;

    public ImportBatchController(ImportBatchService service) {
        this.service = service;
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public ImportPreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") ImportType type) {
        return service.preview(type, file);
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportBatchResponse saveDraft(@Valid @RequestBody SaveImportDraftRequest request) {
        return service.saveDraft(request);
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportBatchResponse create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") ImportType type) {
        return service.createFromExcel(type, file);
    }

    @GetMapping
    public PageResponse<ImportBatchResponse> findAll(
            @RequestParam(required = false) ImportType type,
            @RequestParam(required = false) ImportBatchStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.findAll(type, status, pageable));
    }

    @GetMapping("/{id}")
    public ImportBatchResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBatch(@PathVariable UUID id) {
        service.deleteBatch(id);
    }

    @PutMapping("/{id}/items/{itemId}")
    public ImportBatchResponse updateItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody UpdateImportItemRequest request) {
        return service.updateItem(id, itemId, request.payload());
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ImportBatchResponse deleteItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        return service.deleteItem(id, itemId);
    }

    @PostMapping("/{id}/submit")
    public ImportBatchResponse submit(
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitImportBatchRequest request) {
        Long expectedVersion = request != null ? request.expectedVersion() : null;
        return service.submit(id, expectedVersion);
    }

    @PostMapping("/{id}/approve")
    public ImportBatchResponse approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveImportBatchRequest request) {
        String note = request != null ? request.note() : null;
        Long expectedVersion = request != null ? request.expectedVersion() : null;
        return service.approve(id, note, expectedVersion);
    }

    @PostMapping("/{id}/reject")
    public ImportBatchResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectImportBatchRequest request) {
        return service.reject(id, request.reason(), request.expectedVersion());
    }
}
