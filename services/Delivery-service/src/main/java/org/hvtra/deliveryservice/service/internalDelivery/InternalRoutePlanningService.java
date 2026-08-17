package org.hvtra.deliveryservice.service.internalDelivery;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.model.entity.DeliveryRoute;
import org.hvtra.deliveryservice.model.entity.RouteStop;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.enums.*;
import org.hvtra.deliveryservice.model.request.internal.PlanInternalRoutesRequest;
import org.hvtra.deliveryservice.model.response.internal.InternalEligibilityResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalPlanningRejection;
import org.hvtra.deliveryservice.model.response.internal.InternalPlanningResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteResponse;
import org.hvtra.deliveryservice.repository.DeliveryRouteRepository;
import org.hvtra.deliveryservice.repository.ShipmentRepository;
import org.hvtra.deliveryservice.repository.TenantDeliveryConfigRepository;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalDeliveryResponseMapper;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalDispatchWaveResolver;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalRouteCodeGenerator;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalShipmentTransitionService;
import org.hvtra.deliveryservice.service.internalDelivery.planning.RoutePlanningAlgorithm;
import org.hvtra.deliveryservice.service.internalDelivery.planning.RoutePlanningAlgorithmRegistry;
import org.hvtra.deliveryservice.service.internalDelivery.record.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalRoutePlanningService {
    private final ShipmentRepository shipmentRepository;
    private final TenantDeliveryConfigRepository configRepository;
    private final DeliveryRouteRepository routeRepository;
    private final RoutePlanningAlgorithmRegistry algorithmRegistry;
    private final InternalDispatchWaveResolver waveResolver;
    private final InternalRouteCodeGenerator routeCodeGenerator;
    private final InternalDeliveryResponseMapper responseMapper;
    private final InternalEligibilityService eligibilityService;
    private final InternalShipmentTransitionService transitionService;

    @Transactional
    public InternalPlanningResponse planRoutes(PlanInternalRoutesRequest request) {
        TenantDeliveryConfig config = configRepository.findByTenantIdForUpdate(request.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant delivery config not found: " + request.tenantId()
                ));
        requireInternalPlanningEnabled(config);

        Instant plannedStartAt = request.plannedStartAt() == null ? Instant.now() : request.plannedStartAt();
        DispatchWave wave = waveResolver.resolve(request.dispatchWave(), plannedStartAt, config);
        int candidateLimit = request.resolvedCandidateLimit(config.getMaxOrdersPerRoute());
        List<Shipment> shipments = shipmentRepository.lockPlanningCandidates(
                request.tenantId(),
                DeliveryMode.INTERNAL,
                DeliveryProvider.STORE_COURIER,
                ShipmentStatus.READY_FOR_PLANNING,
                PageRequest.of(0, candidateLimit)
        );

        List<RoutePlanningCandidate> candidates = new ArrayList<>();
        List<InternalPlanningRejection> rejected = new ArrayList<>();
        for (Shipment shipment : shipments) {
            InternalEligibilityResponse eligibility = eligibilityService.evaluate(shipment, config);
            if (!eligibility.eligible()) {
                rejected.add(new InternalPlanningRejection(
                        shipment.getId(),
                        shipment.getShipmentCode(),
                        String.join(",", eligibility.reasons())
                ));
            } else {
                candidates.add(eligibilityService.toPlanningCandidate(shipment));
            }
        }

        if (candidates.isEmpty()) {
            return response(request, config, wave, plannedStartAt, shipments.size(), List.of(), rejected);
        }

        RoutePlanningConstraints constraints = new RoutePlanningConstraints(
                candidates.getFirst().pickup(),
                plannedStartAt,
                request.resolvedMaxRoutes(),
                config.getMaxOrdersPerRoute(),
                config.getMaxRouteWeightGram(),
                config.getMaxRouteCodAmount(),
                config.getMaxRouteDurationMin(),
                config.getAverageSpeedKmh(),
                config.getTravelEstimationMode(),
                config.getServiceTimePerStopMin()
        );
        RoutePlanningAlgorithm algorithm = algorithmRegistry.require(config.getPlanningAlgorithm());
        RoutePlanningOutcome outcome = algorithm.plan(candidates, constraints);
        List<InternalRouteResponse> routes = outcome.routes().stream()
                .map(route -> persistRoute(
                        request.tenantId(),
                        wave,
                        config,
                        plannedStartAt,
                        route
                ))
                .toList();
        outcome.rejected().forEach(item -> rejected.add(new InternalPlanningRejection(
                item.candidate().shipment().getId(),
                item.candidate().shipment().getShipmentCode(),
                item.reason()
        )));
        return response(request, config, wave, plannedStartAt, shipments.size(), routes, rejected);
    }

    private InternalRouteResponse persistRoute(
            UUID tenantId,
            DispatchWave wave,
            TenantDeliveryConfig config,
            Instant plannedStartAt,
            PlannedRoute plannedRoute
    ) {
        DeliveryRoute route = DeliveryRoute.create(
                tenantId,
                routeCodeGenerator.generate(Instant.now()),
                wave
        );
        route.plan(
                plannedStartAt,
                plannedStartAt.plusSeconds(plannedRoute.totalDurationSecond()),
                config.getPlanningAlgorithm().name()
        );
        for (PlannedRouteStop plannedStop : plannedRoute.stops()) {
            Shipment shipment = plannedStop.candidate().shipment();
            RouteStop stop = RouteStop.delivery(
                    shipment,
                    plannedStop.sequenceNo(),
                    plannedStop.plannedArrivalAt()
            );
            stop.configureEstimate(
                    plannedStop.distanceFromPreviousMeter(),
                    plannedStop.travelDurationSecond(),
                    plannedStop.plannedArrivalAt(),
                    plannedStop.estimateSource()

            );
            route.addStop(stop);
            transitionService.transition(
                    shipment,
                    shipment::markPlanned,
                    ShipmentStatusSource.DISPATCHER,
                    route.getRouteCode(),
                    null,
                    null,
                    "route-planner"
            );
        }
        route.updateMetrics(plannedRoute.totalDistanceMeter(), plannedRoute.totalDurationSecond());
        route.markReady();
        return responseMapper.toRouteResponse(routeRepository.save(route));
    }

    private InternalPlanningResponse response(
            PlanInternalRoutesRequest request,
            TenantDeliveryConfig config,
            DispatchWave wave,
            Instant plannedStartAt,
            int candidateCount,
            List<InternalRouteResponse> routes,
            List<InternalPlanningRejection> rejected
    ) {
        return new InternalPlanningResponse(
                request.tenantId(),
                wave,
                config.getPlanningAlgorithm(),
                plannedStartAt,
                candidateCount,
                routes,
                rejected
        );
    }
    private void requireInternalPlanningEnabled(TenantDeliveryConfig config) {
        if (!config.isActive()) {
            throw new IllegalStateException("Tenant delivery config is inactive");
        }
        if (!config.isInternalDeliveryEnabled()) {
            throw new IllegalStateException("Internal delivery is disabled for tenant");
        }
    }
}
