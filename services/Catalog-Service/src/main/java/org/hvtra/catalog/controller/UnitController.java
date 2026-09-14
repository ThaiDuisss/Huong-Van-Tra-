package org.hvtra.catalog.controller;

import jakarta.validation.Valid;
import org.hvtra.catalog.dto.request.StatusRequest;
import org.hvtra.catalog.dto.request.UnitRequest;
import org.hvtra.catalog.dto.response.PageResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.dto.response.LookupResponse;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.enums.UnitType;
import org.hvtra.catalog.service.UnitService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/units")
public class UnitController {
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UnitResponse> findAll(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "type", required = false) UnitType type,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(name, status, type, pageable);
    }

    @GetMapping("/bases")
    public List<UnitResponse> findBases() {
        return service.findBases();
    }

    @GetMapping("/lookup")
    public PageResponse<LookupResponse> lookup(
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.lookup(search, pageable);
    }

    @PostMapping("/batch")
    public List<UnitResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse create(@Valid @RequestBody UnitRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UnitResponse update(
            @PathVariable("id") UUID id, @Valid @RequestBody UnitRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public UnitResponse updateStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportUnits(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) Status status,
            @RequestParam(name = "type", required = false) UnitType type) {
        byte[] excelBytes = service.exportToExcel(search, status, type);
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String rawFilename = "Danh_sach_don_vi_tinh_" + timestamp + ".xlsx";
        String encodedFilename = "";
        try {
            encodedFilename = URLEncoder.encode(rawFilename, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.OK)
    public void importUnits(@RequestParam("file") MultipartFile file) {
        service.importFromExcel(file);
    }

    @GetMapping("/import/template")
    public ResponseEntity<Resource> downloadTemplate() {
        byte[] excelBytes = service.getImportTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_nhap_don_vi.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
