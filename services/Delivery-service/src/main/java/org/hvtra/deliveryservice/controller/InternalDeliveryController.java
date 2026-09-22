package org.hvtra.deliveryservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import org.hvtra.deliveryservice.model.request.internal.*;
import org.hvtra.deliveryservice.model.response.internal.InternalEligibilityResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalPlanningResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteResponse;
import org.hvtra.deliveryservice.model.response.internal.TenantDeliveryConfigResponse;
import org.hvtra.deliveryservice.service.internalDelivery.InternalDeliveryService;
import org.hvtra.deliveryservice.service.internalDelivery.helper.TenantDeliveryConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/internal")
@RequiredArgsConstructor
public class InternalDeliveryController {

    private final TenantDeliveryConfigService configService;
    private final InternalDeliveryService internalDeliveryService;


    @UserAction(value = "DELIVERY_CONFIG_UPDATE", entityType = "TenantDeliveryConfig", entityId = "#tenantId")
    @PutMapping("/tenants/{tenantId}/config")
    public TenantDeliveryConfigResponse configure(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ConfigureTenantDeliveryRequest request
    ) {
        return configService.configure(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/config")
    public TenantDeliveryConfigResponse getConfig(@PathVariable UUID tenantId) {
        return configService.get(tenantId);
    }

    @GetMapping("/shipments/{shipmentId}/eligibility")
    public InternalEligibilityResponse evaluate(@PathVariable UUID shipmentId) {
        return internalDeliveryService.evaluate(shipmentId);
    }

    @UserAction(value = "INTERNAL_SHIPMENT_READY", entityType = "Shipment", entityId = "#shipmentId")
    @PostMapping("/shipments/{shipmentId}/ready")
    public InternalEligibilityResponse markReady(@PathVariable UUID shipmentId) {
        return internalDeliveryService.markReadyForPlanning(shipmentId);
    }

    @UserAction(value = "INTERNAL_SHIPMENT_RETRY", entityType = "Shipment", entityId = "#shipmentId")
    @PostMapping("/shipments/{shipmentId}/retry")
    public InternalEligibilityResponse retry(@PathVariable UUID shipmentId) {
        return internalDeliveryService.retryShipment(shipmentId);
    }

    @UserAction(value = "INTERNAL_ROUTE_PLAN", entityType = "DeliveryRoute")
    @PostMapping("/routes/plan")
    public InternalPlanningResponse plan(@Valid @RequestBody PlanInternalRoutesRequest request) {
        return internalDeliveryService.planRoutes(request);
    }

    @GetMapping("/routes/{routeId}")
    public InternalRouteResponse getRoute(@PathVariable UUID routeId) {
        return internalDeliveryService.getRoute(routeId);
    }

    @GetMapping("/tenants/{tenantId}/routes")
    public List<InternalRouteResponse> listRoutes(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) DeliveryRouteStatus status
    ) {
        return internalDeliveryService.listRoutes(tenantId, status);
    }

    @UserAction(value = "INTERNAL_ROUTE_ASSIGN", entityType = "DeliveryRoute", entityId = "#routeId", detail = "#request.courierName")
    @PostMapping("/routes/{routeId}/assign")
    public InternalRouteResponse assign(
            @PathVariable UUID routeId,
            @Valid @RequestBody AssignInternalCourierRequest request
    ) {
        return internalDeliveryService.assignCourier(routeId, request);
    }

    @UserAction(value = "INTERNAL_ROUTE_START", entityType = "DeliveryRoute", entityId = "#routeId")
    @PostMapping("/routes/{routeId}/start")
    public InternalRouteResponse start(@PathVariable UUID routeId) {
        return internalDeliveryService.startRoute(routeId);
    }

    @UserAction(value = "INTERNAL_ROUTE_CANCEL", entityType = "DeliveryRoute", entityId = "#routeId")
    @PostMapping("/routes/{routeId}/cancel")
    public InternalRouteResponse cancel(
            @PathVariable UUID routeId,
            @RequestBody(required = false) CancelInternalRouteRequest request
    ) {
        return internalDeliveryService.cancelRoute(routeId, request);
    }

    @UserAction(value = "INTERNAL_STOP_ARRIVING", entityType = "DeliveryRoute", entityId = "#routeId", detail = "#stopId")
    @PostMapping("/routes/{routeId}/stops/{stopId}/arriving")
    public InternalRouteResponse markArriving(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId
    ) {
        return internalDeliveryService.markArriving(routeId, stopId);
    }

    @UserAction(value = "INTERNAL_STOP_ARRIVED", entityType = "DeliveryRoute", entityId = "#routeId", detail = "#stopId")
    @PostMapping("/routes/{routeId}/stops/{stopId}/arrived")
    public InternalRouteResponse markArrived(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId
    ) {
        return internalDeliveryService.markArrived(routeId, stopId);
    }

    @UserAction(value = "INTERNAL_STOP_DELIVER", entityType = "DeliveryRoute", entityId = "#routeId", detail = "#stopId")
    @PostMapping("/routes/{routeId}/stops/{stopId}/deliver")
    public InternalRouteResponse deliver(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId,
            @Valid @RequestBody CompleteInternalStopRequest request
    ) {
        return internalDeliveryService.completeStop(routeId, stopId, request);
    }

    @UserAction(value = "INTERNAL_STOP_FAIL", entityType = "DeliveryRoute", entityId = "#routeId", detail = "#stopId")
    @PostMapping("/routes/{routeId}/stops/{stopId}/fail")
    public InternalRouteResponse fail(
            @PathVariable UUID routeId,
            @PathVariable UUID stopId,
            @Valid @RequestBody FailInternalStopRequest request
    ) {
        return internalDeliveryService.failStop(routeId, stopId, request);
    }
}
