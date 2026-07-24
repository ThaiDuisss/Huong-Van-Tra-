package org.hvtra.deliveryservice.controller;

import jakarta.validation.Valid;
import org.hvtra.deliveryservice.carrier.CarrierDeliveryFacade;
import org.hvtra.deliveryservice.carrier.model.*;
import org.hvtra.deliveryservice.carrier.support.CarrierProviderUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/shipments/{shipmentId}/carriers/{provider}")
public class CarrierDeliveryController {

    private final CarrierDeliveryFacade facade;

    public CarrierDeliveryController(CarrierDeliveryFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/quotes")
    public List<CarrierQuoteResult> quote(
            @PathVariable UUID shipmentId,
            @PathVariable String provider,
            @Valid @RequestBody CarrierQuoteCommand command
    ) {
        return facade.quote(shipmentId, CarrierProviderUtils.parse(provider), command);
    }

    @PostMapping("/orders")
    public CarrierShipmentResult create(
            @PathVariable UUID shipmentId,
            @PathVariable String provider,
            @Valid @RequestBody CarrierCreateCommand command
    ) {
        return facade.create(shipmentId, CarrierProviderUtils.parse(provider), command);
    }

    @GetMapping
    public CarrierShipmentResult get(
            @PathVariable UUID shipmentId,
            @PathVariable String provider
    ) {
        return facade.get(shipmentId, CarrierProviderUtils.parse(provider));
    }

    @PostMapping("/refresh")
    public CarrierShipmentResult refresh(
            @PathVariable UUID shipmentId,
            @PathVariable String provider
    ) {
        return facade.refresh(shipmentId, CarrierProviderUtils.parse(provider));
    }

    @PostMapping("/cancel")
    public CarrierShipmentResult cancel(
            @PathVariable UUID shipmentId,
            @PathVariable String provider,
            @RequestBody(required = false) CarrierCancelCommand command
    ) {
        return facade.cancel(shipmentId, CarrierProviderUtils.parse(provider), command);
    }

}
