package org.hvtra.catalog.controller;

import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.service.IngredientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/catalog/ingredients")
public class InternalIngredientController {
    private final IngredientService service;

    public InternalIngredientController(IngredientService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    public List<IngredientResponse> getIngredientsBatch(@RequestBody List<UUID> ids) {
        return service.findByIds(ids);
    }

    @GetMapping("/search-ids")
    public List<UUID> searchIngredientIds(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "unitId", required = false) UUID unitId
    ) {
        return service.searchIngredientIds(keyword, code, name, unitId);
    }
}
