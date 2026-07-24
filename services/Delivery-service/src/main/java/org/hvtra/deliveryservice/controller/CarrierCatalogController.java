package org.hvtra.deliveryservice.controller;

import org.hvtra.deliveryservice.carrier.CarrierGatewayRegistry;
import org.hvtra.deliveryservice.carrier.model.CarrierCapabilities;
import org.hvtra.deliveryservice.carrier.support.CarrierProviderUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/delivery/carriers")
public class CarrierCatalogController {

    private final CarrierGatewayRegistry registry;

    public CarrierCatalogController(CarrierGatewayRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<CarrierCapabilities> supportedCarriers() {
        return registry.supportedProviders().stream()
                .map(registry::capabilities)
                .sorted(Comparator.comparing(capability -> capability.provider().name()))
                .toList();
    }

    @GetMapping("/{provider}/capabilities")
    public CarrierCapabilities capabilities(@PathVariable String provider) {
        return registry.capabilities(CarrierProviderUtils.parse(provider));
    }

}
