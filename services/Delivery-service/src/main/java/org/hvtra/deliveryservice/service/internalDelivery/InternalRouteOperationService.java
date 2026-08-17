package org.hvtra.deliveryservice.service.internalDelivery;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.model.entity.*;
import org.hvtra.deliveryservice.model.enums.*;
import org.hvtra.deliveryservice.model.request.internal.*;
import org.hvtra.deliveryservice.model.response.internal.InternalEligibilityResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteResponse;
import org.hvtra.deliveryservice.repository.*;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalDeliveryResponseMapper;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalShipmentTransitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalRouteOperationService {
    private static final List<DeliveryRouteStatus> COURIER_BUSY_STATUSES = List.of(
            DeliveryRouteStatus.ASSIGNED,
            DeliveryRouteStatus.STARTED
    );

    private final ShipmentRepository shipmentRepository;
    private final TenantDeliveryConfigRepository configRepository;
    private final DeliveryRouteRepository routeRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final DeliveryProofRepository proofRepository;
    private final InternalEligibilityService eligibilityService;
    private final InternalShipmentTransitionService transitionService;
    private final InternalDeliveryResponseMapper responseMapper;
    @Transactional
    public InternalRouteResponse assignCourier(UUID routeId, AssignInternalCourierRequest request) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        boolean sameCourier = Objects.equals(route.getCourierEmployeeId(), request.courierEmployeeId());
        if (!sameCourier && routeRepository.existsByCourierEmployeeIdAndStatusIn(
                request.courierEmployeeId(),
                COURIER_BUSY_STATUSES
        )) {
            throw new IllegalStateException("Courier already has an active route");
        }
        String courierName = request.courierName().trim();
        route.assignCourier(request.courierEmployeeId(), courierName);
        for (RouteStop stop : route.getStops()) {
            Shipment shipment = stop.getShipment();
            transitionService.transition(
                    shipment,
                    () -> shipment.assignCourier(request.courierEmployeeId(), courierName),
                    ShipmentStatusSource.DISPATCHER,
                    route.getRouteCode(),
                    null,
                    null,
                    "dispatcher"
            );
        }
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse startRoute(UUID routeId) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        if (route.getStatus() == DeliveryRouteStatus.STARTED) {
            return responseMapper.toRouteResponse(route);
        }
        Instant startedAt = Instant.now();
        route.start(startedAt);
        for (RouteStop stop : route.getStops()) {
            Shipment shipment = stop.getShipment();
            transitionService.transition(
                    shipment,
                    shipment::markReadyForPickup,
                    ShipmentStatusSource.COURIER,
                    route.getRouteCode(),
                    null,
                    null,
                    route.getCourierNameSnapshot()
            );
            transitionService.transition(
                    shipment,
                    () -> shipment.markPickedUp(startedAt),
                    ShipmentStatusSource.COURIER,
                    route.getRouteCode(),
                    null,
                    null,
                    route.getCourierNameSnapshot()
            );
            transitionService.transition(
                    shipment,
                    shipment::markInTransit,
                    ShipmentStatusSource.COURIER,
                    route.getRouteCode(),
                    null,
                    null,
                    route.getCourierNameSnapshot()
            );
        }
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse markArriving(UUID routeId, UUID stopId) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        RouteStop stop = requireStop(route, stopId);
        requireStartedRoute(route);
        if (stop.getStatus() == RouteStopStatus.PENDING) {
            stop.markArriving();
        } else if (stop.getStatus() != RouteStopStatus.ARRIVING) {
            throw new IllegalStateException("Stop cannot be marked arriving from " + stop.getStatus());
        }
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse markArrived(UUID routeId, UUID stopId) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        RouteStop stop = requireStop(route, stopId);
        requireStartedRoute(route);
        if (stop.getStatus() == RouteStopStatus.ARRIVED) {
            return responseMapper.toRouteResponse(route);
        }
        stop.markArrived(Instant.now());
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse completeStop(
            UUID routeId,
            UUID stopId,
            CompleteInternalStopRequest request
    ) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        RouteStop stop = requireStop(route, stopId);
        if (stop.getStatus() == RouteStopStatus.DELIVERED) {
            return responseMapper.toRouteResponse(route);
        }
        requireStartedRoute(route);
        validateCompletionRequest(request);
        if (stop.getStatus() == RouteStopStatus.PENDING) {
            stop.markArriving();
        }

        Shipment shipment = stop.getShipment();
        Instant completedAt = Instant.now();
        DeliveryAttempt attempt = DeliveryAttempt.start(
                shipment,
                route,
                route.getCourierEmployeeId(),
                nextAttemptNo(shipment.getId()),
                stop.getActualArrivalAt() == null ? completedAt : stop.getActualArrivalAt()
        );
        attempt.succeed(
                request.collectedCodAmount(),
                request.receiverName(),
                request.receiverRelationship(),
                request.latitude(),
                request.longitude(),
                completedAt
        );
        attemptRepository.save(attempt);
        List<DeliveryProof> proofs = request.resolvedProofs().stream()
                .map(command -> toProof(shipment, attempt, command, request, completedAt))
                .toList();
        proofRepository.saveAll(proofs);
        stop.markDelivered(completedAt);
        transitionService.transition(
                shipment,
                () -> shipment.markDelivered(completedAt, request.collectedCodAmount()),
                ShipmentStatusSource.COURIER,
                route.getRouteCode(),
                null,
                null,
                route.getCourierNameSnapshot()
        );
        completeRouteIfFinished(route, completedAt);
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse failStop(UUID routeId, UUID stopId, FailInternalStopRequest request) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        RouteStop stop = requireStop(route, stopId);
        if (stop.getStatus() == RouteStopStatus.FAILED) {
            return responseMapper.toRouteResponse(route);
        }
        requireStartedRoute(route);
        if (request.failureStatus() == DeliveryAttemptStatus.STARTED
                || request.failureStatus() == DeliveryAttemptStatus.SUCCEEDED) {
            throw new IllegalArgumentException("failureStatus must represent a failed attempt");
        }
        Shipment shipment = stop.getShipment();
        Instant failedAt = Instant.now();
        DeliveryAttempt attempt = DeliveryAttempt.start(
                shipment,
                route,
                route.getCourierEmployeeId(),
                nextAttemptNo(shipment.getId()),
                stop.getActualArrivalAt() == null ? failedAt : stop.getActualArrivalAt()
        );
        attempt.fail(
                request.failureStatus(),
                request.failureCode(),
                request.failureReason(),
                request.note(),
                failedAt
        );
        attemptRepository.save(attempt);
        stop.markFailed(request.failureCode(), request.failureReason(), failedAt);
        transitionService.transition(
                shipment,
                () -> shipment.markDeliveryFailed(request.failureCode(), request.failureReason()),
                ShipmentStatusSource.COURIER,
                route.getRouteCode(),
                request.failureCode(),
                request.failureReason(),
                route.getCourierNameSnapshot()
        );
        completeRouteIfFinished(route, failedAt);
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalRouteResponse cancelRoute(UUID routeId, CancelInternalRouteRequest request) {
        DeliveryRoute route = requireRouteForUpdate(routeId);
        if (route.getStatus() == DeliveryRouteStatus.CANCELLED) {
            return responseMapper.toRouteResponse(route);
        }
        String reason = request == null
                ? "Route cancelled by dispatcher"
                : request.resolvedReason();
        route.cancel();
        for (RouteStop stop : route.getStops()) {
            if (stop.getStatus() == RouteStopStatus.PENDING) {
                stop.skip(reason);
            }
            transitionService.transition(
                    stop.getShipment(),
                    stop.getShipment()::releaseFromInternalRoute,
                    ShipmentStatusSource.DISPATCHER,
                    route.getRouteCode(),
                    "ROUTE_CANCELLED",
                    reason,
                    "dispatcher"
            );
        }
        return responseMapper.toRouteResponse(route);
    }

    @Transactional
    public InternalEligibilityResponse retryShipment(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
        if (shipment.getStatus() != ShipmentStatus.DELIVERY_FAILED) {
            throw new IllegalStateException("Only failed internal shipment can be retried");
        }
        if (routeRepository.countByShipmentIdAndStatusIn(
                shipmentId,
                List.of(DeliveryRouteStatus.STARTED)
        ) > 0) {
            throw new IllegalStateException("Previous route must finish before retrying shipment");
        }
        TenantDeliveryConfig config = configRepository.findByTenantId(shipment.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant delivery config not found: " + shipment.getTenantId()
                ));
        InternalEligibilityResponse eligibility = eligibilityService.evaluate(shipment, config);
        if (!eligibility.eligible()) {
            throw new IllegalStateException("Shipment is no longer eligible for internal delivery: "
                    + String.join(", ", eligibility.reasons()));
        }
        transitionService.transition(
                shipment,
                shipment::retryInternalDelivery,
                ShipmentStatusSource.DISPATCHER,
                "internal-retry",
                null,
                null,
                "dispatcher"
        );
        return eligibilityService.evaluate(shipment, config);
    }

    @Transactional(readOnly = true)
    public InternalRouteResponse getRoute(UUID routeId) {
        DeliveryRoute route = routeRepository.findDetailedById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Internal route not found: " + routeId));
        return responseMapper.toRouteResponse(route);
    }


    @Transactional(readOnly = true)
    public List<InternalRouteResponse> listRoutes(UUID tenantId, DeliveryRouteStatus status) {
        List<DeliveryRoute> routes = status == null
                ? routeRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                : routeRepository.findAllByTenantIdAndStatus(tenantId, status);
        return routes.stream().map(responseMapper::toRouteResponse).toList();
    }

    private DeliveryProof toProof(
            Shipment shipment,
            DeliveryAttempt attempt,
            InternalDeliveryProofCommand command,
            CompleteInternalStopRequest request,
            Instant capturedAt
    ) {
        return DeliveryProof.create(
                shipment,
                attempt,
                command.proofType(),
                command.fileId(),
                command.otpVerified(),
                request.receiverName(),
                request.latitude(),
                request.longitude(),
                capturedAt
        );
    }

    private void validateCompletionRequest(CompleteInternalStopRequest request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        for (InternalDeliveryProofCommand proof : request.resolvedProofs()) {
            if ((proof.proofType() == DeliveryProofType.PHOTO
                    || proof.proofType() == DeliveryProofType.SIGNATURE)
                    && proof.fileId() == null) {
                throw new IllegalArgumentException("fileId is required for photo or signature proof");
            }
            if (proof.proofType() == DeliveryProofType.OTP && !proof.otpVerified()) {
                throw new IllegalArgumentException("OTP proof must be verified");
            }
            if (proof.proofType() == DeliveryProofType.RECEIVER_CONFIRMATION
                    && (request.receiverName() == null || request.receiverName().isBlank())) {
                throw new IllegalArgumentException("receiverName is required for receiver confirmation");
            }
        }
    }


    private int nextAttemptNo(UUID shipmentId) {
        return attemptRepository.findTopByShipmentIdOrderByAttemptNoDesc(shipmentId)
                .map(attempt -> attempt.getAttemptNo() + 1)
                .orElse(1);
    }

    private void completeRouteIfFinished(DeliveryRoute route, Instant completedAt) {
        if (route.getStops().stream().allMatch(RouteStop::isTerminal)) {
            route.complete(completedAt);
        }
    }
    private DeliveryRoute requireRouteForUpdate(UUID routeId) {
        return routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Internal route not found: " + routeId));
    }

    private RouteStop requireStop(DeliveryRoute route, UUID stopId) {
        return route.getStops().stream()
                .filter(stop -> Objects.equals(stop.getId(), stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Route stop not found in route: " + stopId
                ));
    }


    private void requireStartedRoute(DeliveryRoute route) {
        if (route.getStatus() != DeliveryRouteStatus.STARTED) {
            throw new IllegalStateException("Route must be started");
        }
    }
}

