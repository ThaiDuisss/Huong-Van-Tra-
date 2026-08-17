package org.hvtra.deliveryservice.controller;

import org.hvtra.deliveryservice.model.response.ahmove.AhamoveCityResponse;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveServiceResponse;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveSpecialRequestResponse;
import org.hvtra.deliveryservice.service.ahmove.AhamoveCatalogService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping({"/api/delivery/carriers/AHAMOVE", "/api/delivery/carriers/ahamove"})
public class AhamoveCatalogController {

    private final AhamoveCatalogService catalogService;

    public AhamoveCatalogController(AhamoveCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/cities")
    public List<AhamoveCityResponse> cities(
            @RequestParam(defaultValue = "VN") String countryId
    ) {
        return catalogService.cities(countryId);
    }

    @GetMapping("/services")
    public List<AhamoveServiceResponse> services(
            @RequestParam(required = false) String cityId,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String deliveryType
    ) {
        return catalogService.services(cityId, latitude, longitude, deliveryType);
    }

    @GetMapping("/services/{serviceId}/special-requests")
    public List<AhamoveSpecialRequestResponse> specialRequests(@PathVariable String serviceId) {
        return catalogService.specialRequests(serviceId);
    }

    @GetMapping("/services/{serviceId}")
    public List<AhamoveServiceResponse> serviceDetail(@PathVariable String serviceId) {
        return catalogService.serviceDetail(serviceId);
    }
}
