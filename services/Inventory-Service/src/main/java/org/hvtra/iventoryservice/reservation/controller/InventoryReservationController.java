package org.hvtra.iventoryservice.reservation.controller;

import jakarta.validation.Valid;
import org.hvtra.iventoryservice.reservation.dto.request.ApplyInventoryReservationRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ConfirmInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationItemRequest;
import org.hvtra.iventoryservice.reservation.dto.request.ReleaseInventoryReservationOrderRequest;
import org.hvtra.iventoryservice.reservation.dto.response.InventoryReservationResponse;
import org.hvtra.iventoryservice.reservation.service.InventoryReservationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/reservations")
public class InventoryReservationController {

    private final InventoryReservationService service;

    public InventoryReservationController(InventoryReservationService service) {
        this.service = service;
    }

    @PostMapping("/apply")
    public InventoryReservationResponse apply(@Valid @RequestBody ApplyInventoryReservationRequest request) {
        return service.apply(request);
    }

    @PostMapping("/release-item")
    public InventoryReservationResponse releaseItem(@Valid @RequestBody ReleaseInventoryReservationItemRequest request) {
        return service.releaseItem(request);
    }

    @PostMapping("/release-order")
    public List<InventoryReservationResponse> releaseOrder(@Valid @RequestBody ReleaseInventoryReservationOrderRequest request) {
        return service.releaseOrder(request);
    }

    @PostMapping("/confirm-order")
    public List<InventoryReservationResponse> confirmOrder(@Valid @RequestBody ConfirmInventoryReservationOrderRequest request) {
        return service.confirmOrder(request);
    }

    @PostMapping("/expire")
    public int expire() {
        return service.expireReservations();
    }

    @org.springframework.web.bind.annotation.GetMapping("/expiring-soon")
    public List<InventoryReservationResponse> getExpiringSoon(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "60") int withinMinutes) {
        return service.getExpiringReservations(withinMinutes);
    }
}
