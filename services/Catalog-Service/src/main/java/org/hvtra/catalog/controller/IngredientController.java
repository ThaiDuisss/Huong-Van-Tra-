package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.service.IngredientService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.multipart.MultipartFile;

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
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(search, status, pageable);
    }

    @GetMapping("/{id}")
    public IngredientResponse findById(@PathVariable("id") UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(@Valid @RequestBody IngredientRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public IngredientResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody IngredientRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public IngredientResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

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

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public void importIngredients(
            @RequestParam("file") MultipartFile file) {
        service.importFromExcel(file);
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
