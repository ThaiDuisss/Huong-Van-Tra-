package org.hvtra.iventoryservice.item.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.item.dto.request.InventoryItemRequest;
import org.hvtra.iventoryservice.item.dto.response.InventoryItemResponse;
import org.hvtra.iventoryservice.item.service.InventoryItemService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@RestController
@RequestMapping("/internal/inventory/inventory-items")
public class InternalInventoryItemController {
    private final InventoryItemService service;

    public InternalInventoryItemController(InventoryItemService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return service.create(request);
    }

    @PutMapping("/by-reference/{itemType}/{referenceId}")
    public InventoryItemResponse updateByReference(
            @PathVariable("itemType") InventoryItemType itemType,
            @PathVariable("referenceId") UUID referenceId,
            @Valid @RequestBody InventoryItemRequest request) {
        return service.updateByReference(referenceId, itemType, request);
    }
}
