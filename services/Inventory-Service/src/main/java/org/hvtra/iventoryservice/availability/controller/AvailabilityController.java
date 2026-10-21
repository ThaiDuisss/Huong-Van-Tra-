package org.hvtra.iventoryservice.availability.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateRequest;
import org.hvtra.iventoryservice.availability.dto.AvailabilityEstimateResponse;
import org.hvtra.iventoryservice.availability.service.AvailabilityEstimateService;
import org.hvtra.iventoryservice.availability.service.BackorderPreparationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AvailabilityController {

    private final AvailabilityEstimateService estimateService;
    private final BackorderPreparationService preparationService;

    public AvailabilityController(AvailabilityEstimateService estimateService,
                                  BackorderPreparationService preparationService) {
        this.estimateService = estimateService;
        this.preparationService = preparationService;
    }

    /** Ước tính ngày có hàng cho phần khách đặt vượt quá tồn. Chỉ đọc. */
    @PostMapping({"/api/inventory/availability/estimate", "/internal/inventory/availability/estimate"})
    public AvailabilityEstimateResponse estimate(@Valid @RequestBody AvailabilityEstimateRequest request) {
        return estimateService.estimate(request);
    }

    /** Đơn đã thu tiền nhưng chưa có hàng: tạo lệnh sản xuất theo đơn / cảnh báo nhập nguyên liệu. */
    @PostMapping("/internal/inventory/availability/backorders/prepare")
    public AvailabilityEstimateResponse prepare(@Valid @RequestBody AvailabilityEstimateRequest request) {
        return preparationService.prepare(request);
    }
}
