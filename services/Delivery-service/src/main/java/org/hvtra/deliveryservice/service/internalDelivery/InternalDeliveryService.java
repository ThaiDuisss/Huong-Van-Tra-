package org.hvtra.deliveryservice.service.internalDelivery;

import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import org.hvtra.deliveryservice.model.request.internal.*;

import org.hvtra.deliveryservice.model.response.internal.InternalEligibilityResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalPlanningResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class InternalDeliveryService {

    private final InternalEligibilityService eligibilityService;
    private final InternalRoutePlanningService planningService;
    private final InternalRouteOperationService operationService;

    public InternalDeliveryService(
            InternalEligibilityService eligibilityService,
            InternalRoutePlanningService planningService,
            InternalRouteOperationService operationService
    ) {
        this.eligibilityService = eligibilityService;
        this.planningService = planningService;
        this.operationService = operationService;
    }

    public InternalEligibilityResponse evaluate(UUID shipmentId) {
        return eligibilityService.evaluate(shipmentId);
    }

    public InternalEligibilityResponse markReadyForPlanning(UUID shipmentId) {
        return eligibilityService.markReadyForPlanning(shipmentId);
    }

    public InternalPlanningResponse planRoutes(PlanInternalRoutesRequest request) {
        return planningService.planRoutes(request);
    }

    public InternalRouteResponse assignCourier(UUID routeId, AssignInternalCourierRequest request) {
        return operationService.assignCourier(routeId, request);
    }

    public InternalRouteResponse startRoute(UUID routeId) {
        return operationService.startRoute(routeId);
    }

    public InternalRouteResponse markArriving(UUID routeId, UUID stopId) {
        return operationService.markArriving(routeId, stopId);
    }

    public InternalRouteResponse markArrived(UUID routeId, UUID stopId) {
        return operationService.markArrived(routeId, stopId);
    }

    public InternalRouteResponse completeStop(
            UUID routeId,
            UUID stopId,
            CompleteInternalStopRequest request
    ) {
        return operationService.completeStop(routeId, stopId, request);
    }

    public InternalRouteResponse failStop(UUID routeId, UUID stopId, FailInternalStopRequest request) {
        return operationService.failStop(routeId, stopId, request);
    }

    public InternalRouteResponse cancelRoute(UUID routeId, CancelInternalRouteRequest request) {
        return operationService.cancelRoute(routeId, request);
    }

    public InternalEligibilityResponse retryShipment(UUID shipmentId) {
        return operationService.retryShipment(shipmentId);
    }

    public InternalRouteResponse getRoute(UUID routeId) {
        return operationService.getRoute(routeId);
    }

    public List<InternalRouteResponse> listRoutes(UUID tenantId, DeliveryRouteStatus status) {
        return operationService.listRoutes(tenantId, status);
    }
}
