package org.hvtra.iventoryservice.replenishmentpolicy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.request.ReplenishmentPolicyRequest;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyMatrixRowResponse;
import org.hvtra.iventoryservice.replenishmentpolicy.dto.response.ReplenishmentPolicyResponse;
import org.hvtra.iventoryservice.replenishmentpolicy.service.ReplenishmentPolicyService;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/replenishment-policies")
@RequiredArgsConstructor
public class ReplenishmentPolicyController {

    private final ReplenishmentPolicyService policyService;

    @GetMapping
    public PageResponse<ReplenishmentPolicyResponse> findAll(
            @RequestParam(name = "inventoryItemId", required = false) UUID inventoryItemId,
            @RequestParam(name = "locationType", required = false) InventoryLocationType locationType,
            @RequestParam(name = "itemType", required = false) InventoryItemType itemType,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return policyService.findAll(inventoryItemId, locationType, itemType, enabled, search, pageable);
    }

    @GetMapping("/matrix")
    public PageResponse<ReplenishmentPolicyMatrixRowResponse> findMatrix(
            @RequestParam(name = "itemType", required = false) InventoryItemType itemType,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return policyService.findMatrix(itemType, search, pageable);
    }

    @GetMapping("/{id}")
    public ReplenishmentPolicyResponse findById(@PathVariable("id") UUID id) {
        return policyService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @UserAction(value = "REPLENISHMENT_POLICY_CREATE", entityType = "InventoryReplenishmentPolicy", entityId = "#result.id")
    public ReplenishmentPolicyResponse create(@Valid @RequestBody ReplenishmentPolicyRequest request) {
        return policyService.create(request);
    }

    @PutMapping("/{id}")
    @UserAction(value = "REPLENISHMENT_POLICY_UPDATE", entityType = "InventoryReplenishmentPolicy", entityId = "#id")
    public ReplenishmentPolicyResponse update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReplenishmentPolicyRequest request
    ) {
        return policyService.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    @UserAction(value = "REPLENISHMENT_POLICY_TOGGLE", entityType = "InventoryReplenishmentPolicy", entityId = "#id")
    public ReplenishmentPolicyResponse toggleEnabled(
            @PathVariable("id") UUID id,
            @RequestParam("enabled") boolean enabled
    ) {
        return policyService.toggleEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @UserAction(value = "REPLENISHMENT_POLICY_DELETE", entityType = "InventoryReplenishmentPolicy", entityId = "#id")
    public void delete(@PathVariable("id") UUID id) {
        policyService.delete(id);
    }
}
