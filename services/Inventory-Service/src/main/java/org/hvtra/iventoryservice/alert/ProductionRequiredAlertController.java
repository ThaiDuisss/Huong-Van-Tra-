package org.hvtra.iventoryservice.alert;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/inventory/production-alerts")
public class ProductionRequiredAlertController {
    private final ProductionRequiredAlertService service;

    @PostMapping
    public void createIfAbsent(@Valid @RequestBody ProductionRequiredAlertRequest request) {
        service.createIfAbsent(request);
    }
}
