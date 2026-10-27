package org.hvtra.iventoryservice.bundle.controller;

import jakarta.validation.Valid;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.iventoryservice.bundle.dto.request.ConfirmCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleAvailabilityRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService;
import org.hvtra.iventoryservice.bundle.service.CustomBundlePackingService;
import org.hvtra.iventoryservice.domain.enums.CustomBundlePackingStatus;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Đóng trà theo yêu cầu: POS xem tồn nguyên liệu, nhân viên kho đóng gói. */
@RestController
@RequestMapping("/api/inventory/custom-bundles")
public class CustomBundleController {

    private final CustomBundleAvailabilityService availabilityService;
    private final CustomBundlePackingService packingService;

    public CustomBundleController(
            CustomBundleAvailabilityService availabilityService,
            CustomBundlePackingService packingService) {
        this.availabilityService = availabilityService;
        this.packingService = packingService;
    }

    /** Tồn nguyên liệu + ngày dự kiến có đủ cho bundle khách đang chọn. Chỉ đọc. */
    @PostMapping("/availability")
    public CustomBundleAvailabilityResponse availability(@Valid @RequestBody CustomBundleAvailabilityRequest request) {
        return availabilityService.check(request.branchId(), request.lines());
    }

    @GetMapping("/packings/statuses")
    public List<String> statuses() {
        return Arrays.stream(CustomBundlePackingStatus.values()).map(Enum::name).toList();
    }

    @GetMapping("/packings")
    public PageResponse<CustomBundlePackingResponse> findAll(
            @RequestParam(required = false) CustomBundlePackingStatus status,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return packingService.findAll(status, branchId, search, pageable);
    }

    @GetMapping("/packings/{id}")
    public CustomBundlePackingResponse findById(@PathVariable UUID id) {
        return packingService.findById(id);
    }

    @UserAction(value = "CUSTOM_BUNDLE_RECHECK", entityType = "CustomBundlePacking", entityId = "#id")
    @PostMapping("/packings/{id}/recheck")
    public CustomBundlePackingResponse recheck(@PathVariable UUID id) {
        return packingService.recheck(id);
    }

    @UserAction(value = "CUSTOM_BUNDLE_PACK", entityType = "CustomBundlePacking", entityId = "#id")
    @PostMapping("/packings/{id}/confirm")
    public CustomBundlePackingResponse confirm(
            @PathVariable UUID id, @Valid @RequestBody ConfirmCustomBundlePackingRequest request) {
        return packingService.confirm(id, request);
    }
}
