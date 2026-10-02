package org.hvtra.iventoryservice.sale.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.sale.dto.FulfillSaleRequest;
import org.hvtra.iventoryservice.sale.dto.FulfillSaleResponse;
import org.hvtra.iventoryservice.sale.service.SaleFulfillmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory/sales")
public class InternalSaleFulfillmentController {
    private final SaleFulfillmentService service;

    public InternalSaleFulfillmentController(SaleFulfillmentService service) {
        this.service = service;
    }

    @PostMapping("/fulfill")
    public FulfillSaleResponse fulfill(@Valid @RequestBody FulfillSaleRequest request) {
        return service.fulfill(request);
    }
}
