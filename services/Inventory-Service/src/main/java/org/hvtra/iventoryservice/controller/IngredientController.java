package org.hvtra.iventoryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.dto.request.IngredientRequest;
import org.hvtra.iventoryservice.dto.request.StatusRequest;
import org.hvtra.iventoryservice.dto.response.IngredientResponse;
import org.hvtra.iventoryservice.dto.response.PageResponse;
import org.hvtra.iventoryservice.enums.Status;
import org.hvtra.iventoryservice.service.IngredientService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ingredients")
public class IngredientController {
    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<IngredientResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.findAll(search, status, pageable);
    }

    @GetMapping("/{id}")
    public IngredientResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IngredientResponse create(@Valid @RequestBody IngredientRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public IngredientResponse update(
            @PathVariable Long id, @Valid @RequestBody IngredientRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public IngredientResponse updateStatus(
            @PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
