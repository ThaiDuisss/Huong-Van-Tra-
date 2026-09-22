package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.IngredientService;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/catalog/ingredients")
public class IngredientController {
    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<IngredientResponse> findAll(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "supplierId", required = false) UUID supplierId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(search, status, pageable, supplierId);
    }

    @GetMapping("/{id}")
    public IngredientResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping("/batch")
    public java.util.List<IngredientResponse> getIngredientsBatch(@RequestBody java.util.List<UUID> ids) {
        return service.findByIds(ids);
    }

    @GetMapping("/search-ids")
    public java.util.List<UUID> searchIngredientIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "unitId", required = false) UUID unitId
    ) {
        return service.searchIngredientIds(keyword, code, name, unitId);
    }

    @UserAction(value = "INGREDIENT_CREATE", entityType = "Ingredient", entityId = "#result.id")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(@Valid @RequestBody IngredientRequest request) {
        return service.create(request);
    }

    @UserAction(value = "INGREDIENT_UPDATE", entityType = "Ingredient", entityId = "#id")
    @PutMapping("/{id}")
    public IngredientResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody IngredientRequest request) {
        return service.update(id, request);
    }

    @UserAction(value = "INGREDIENT_STATUS_UPDATE", entityType = "Ingredient", entityId = "#id", detail = "#request.status")
    @PatchMapping("/{id}/status")
    public IngredientResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @UserAction(value = "INGREDIENT_DELETE", entityType = "Ingredient", entityId = "#id")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportIngredients(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) Status status) {
        byte[] excelBytes = service.exportToExcel(search, status);
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String rawFilename = "Danh_sach_nguyen_lieu_" + timestamp + ".xlsx";
        String encodedFilename = "";
        try {
            encodedFilename = URLEncoder.encode(rawFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @GetMapping("/import/template")
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] excelBytes = service.getImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_nguyen_lieu.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
