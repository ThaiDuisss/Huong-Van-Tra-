package org.hvtra.deliveryservice.controller;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.deliveryservice.model.request.ghn.GhnCancelShipmentCommand;
import org.hvtra.deliveryservice.model.request.ghn.GhnCreateShipmentCommand;
import org.hvtra.deliveryservice.model.request.ghn.GhnQuoteCommand;
import org.hvtra.deliveryservice.model.response.ghn.GhnQuoteResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnServiceResponse;
import org.hvtra.deliveryservice.model.response.ghn.GhnShipmentResponse;
import org.hvtra.deliveryservice.service.ghn.GhnService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/shipments/{shipmentId}/ghn")
@RequiredArgsConstructor
public class GhnDeliveryController {

    private final GhnService ghnIntegrationService;

    @GetMapping("/services")
    public List<GhnServiceResponse> getAvailableServices(@PathVariable UUID shipmentId) {
        return ghnIntegrationService.getAvailableServices(shipmentId);
    }

    @PostMapping("/fee")
    public GhnQuoteResponse calculateFee(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody GhnQuoteCommand command
    ) {
        return ghnIntegrationService.calculateFee(shipmentId, command);
    }

    @UserAction(value = "GHN_SHIPMENT_CREATE", entityType = "Shipment", entityId = "#shipmentId")
    @PostMapping("/orders")
    public GhnShipmentResponse createOrder(
            @PathVariable UUID shipmentId,
            @Valid @RequestBody GhnCreateShipmentCommand command
    ) {
        return ghnIntegrationService.createShipment(shipmentId, command);
    }

    @PostMapping("/refresh")
    public GhnShipmentResponse refresh(@PathVariable UUID shipmentId) {
        return ghnIntegrationService.refreshShipment(shipmentId);
    }

    @UserAction(value = "GHN_SHIPMENT_CANCEL", entityType = "Shipment", entityId = "#shipmentId")
    @PostMapping("/cancel")
    public GhnShipmentResponse cancel(
            @PathVariable UUID shipmentId,
            @RequestBody(required = false) GhnCancelShipmentCommand command
    ) {
        return ghnIntegrationService.cancelShipment(shipmentId, command == null ? null : command.reason());
    }

    @GetMapping
    public GhnShipmentResponse get(@PathVariable UUID shipmentId) {
        return ghnIntegrationService.getShipmentResponse(shipmentId);
    }
}
