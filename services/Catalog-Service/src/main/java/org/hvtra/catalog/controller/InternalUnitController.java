package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.service.UnitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/units")
public class InternalUnitController {
    private final UnitService service;

    public InternalUnitController(UnitService service) {
        this.service = service;
    }

    /** Toàn bộ đơn vị — dùng để resolve theo tên/ký hiệu lúc service khác cần tạo nguyên liệu mới. */
    @GetMapping
    public List<UnitResponse> getAllUnits() {
        return service.findAll();
    }

    @PostMapping("/batch")
    public List<UnitResponse> findByIds(@RequestBody List<UUID> ids) {
        return service.findAllByIds(ids);
    }
}
