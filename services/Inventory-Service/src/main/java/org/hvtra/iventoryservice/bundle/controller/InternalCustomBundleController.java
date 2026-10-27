package org.hvtra.iventoryservice.bundle.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.bundle.dto.request.CancelCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CreateCustomBundlePackingRequest;
import org.hvtra.iventoryservice.bundle.dto.request.CustomBundleAvailabilityRequest;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundleAvailabilityResponse;
import org.hvtra.iventoryservice.bundle.dto.response.CustomBundlePackingResponse;
import org.hvtra.iventoryservice.bundle.service.CustomBundleAvailabilityService;
import org.hvtra.iventoryservice.bundle.service.CustomBundlePackingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Order-Service gọi qua Feign cho dòng bundle của đơn bán. */
@RestController
@RequestMapping("/internal/inventory/custom-bundles")
public class InternalCustomBundleController {

    private final CustomBundleAvailabilityService availabilityService;
    private final CustomBundlePackingService packingService;

    public InternalCustomBundleController(
            CustomBundleAvailabilityService availabilityService,
            CustomBundlePackingService packingService) {
        this.availabilityService = availabilityService;
        this.packingService = packingService;
    }

    @PostMapping("/availability")
    public CustomBundleAvailabilityResponse availability(@Valid @RequestBody CustomBundleAvailabilityRequest request) {
        return availabilityService.check(request.branchId(), request.lines());
    }

    @PostMapping("/packings")
    public CustomBundlePackingResponse create(@Valid @RequestBody CreateCustomBundlePackingRequest request) {
        return packingService.create(request);
    }

    @GetMapping("/packings/by-order/{orderId}")
    public List<CustomBundlePackingResponse> findByOrder(@PathVariable UUID orderId) {
        return packingService.findByOrder(orderId);
    }

    @PostMapping("/packings/cancel-by-order")
    public List<CustomBundlePackingResponse> cancelByOrder(@Valid @RequestBody CancelCustomBundlePackingRequest request) {
        return packingService.cancelByOrder(request);
    }
}
