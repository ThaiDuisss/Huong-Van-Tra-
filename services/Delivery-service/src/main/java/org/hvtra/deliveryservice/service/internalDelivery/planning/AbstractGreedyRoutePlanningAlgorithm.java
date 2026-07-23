package org.hvtra.deliveryservice.service.internalDelivery.planning;

import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;
import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.record.*;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RouteTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.TravelEstimate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
@RequiredArgsConstructor
public abstract class AbstractGreedyRoutePlanningAlgorithm implements RoutePlanningAlgorithm{
    private final HaversineDistanceCalculator distanceCalculator;
    private final RouteTravelEstimator travelEstimator;


    protected abstract Comparator<RoutePlanningCandidate> candidateOrder(GeoPoint currentPoint);

    @Override
    public RoutePlanningOutcome plan(List<RoutePlanningCandidate> candidates, RoutePlanningConstraints constraints) {
        List<RoutePlanningCandidate> remaining = new ArrayList<>(candidates);
        List<PlannedRoute> routes = new ArrayList<>();
        List<RejectedPlanningCandidate> rejected = new ArrayList<>();

        while (!remaining.isEmpty() && routes.size() < constraints.maxRoutes()) {
            MutableRoute route = new MutableRoute(constraints);
            boolean added;
            do {
                added = addNextCandidate(route, remaining, constraints);
            } while (added && route.stops.size() < constraints.maxOrdersPerRoute());

            if (route.stops.isEmpty()) {
                remaining.forEach(candidate -> rejected.add(new RejectedPlanningCandidate(
                        candidate,
                        "ROUTE_CONSTRAINTS_UNSATISFIED"
                )));
                remaining.clear();
            } else {
                routes.add(route.toPlannedRoute());
            }
        }

        remaining.forEach(candidate -> rejected.add(new RejectedPlanningCandidate(
                candidate,
                "MAX_ROUTES_REACHED"
        )));
        return new RoutePlanningOutcome(routes, rejected);
    }
    private boolean addNextCandidate(
            MutableRoute route,
            List<RoutePlanningCandidate> remaining,
            RoutePlanningConstraints constraints
    ) {
        List<RoutePlanningCandidate> ordered = remaining.stream()
                .sorted(candidateOrder(route.currentPoint))
                .toList();
        for (RoutePlanningCandidate candidate : ordered) {
            CandidateEstimate estimate = estimate(route, candidate, constraints);
            if (estimate.accepted()) {
                route.add(candidate, estimate);
                remaining.remove(candidate);
                return true;
            }
        }
        return false;
    }

    private CandidateEstimate estimate(
            MutableRoute route,
            RoutePlanningCandidate candidate,
            RoutePlanningConstraints constraints
    ) {
        if (candidate.shipment().getTotalWeightGram() < 0 || candidate.shipment().getCodAmount() < 0) {
            return CandidateEstimate.rejected();
        }
        long newWeight = (long) route.totalWeightGram + candidate.shipment().getTotalWeightGram();
        if (newWeight > Integer.MAX_VALUE) {
            return CandidateEstimate.rejected();
        }
        long newCod;
        try {
            newCod = Math.addExact(route.totalCodAmount, candidate.shipment().getCodAmount());
        } catch (ArithmeticException exception) {
            return CandidateEstimate.rejected();
        }
        if (constraints.maxRouteWeightGram() != null && newWeight > constraints.maxRouteWeightGram()) {
            return CandidateEstimate.rejected();
        }
        if (constraints.maxRouteCodAmount() != null && newCod > constraints.maxRouteCodAmount()) {
            return CandidateEstimate.rejected();
        }

        TravelEstimate travelEstimate = travelEstimator.estimate(
                route.currentPoint,
                candidate.destination(),
                route.currentTime,
                constraints.averageSpeedKmh(),
                constraints.travelEstimationMode()
        );
        int distanceMeter = travelEstimate.distanceMeter();
        if ((long) route.totalDistanceMeter + distanceMeter > Integer.MAX_VALUE) {
            return CandidateEstimate.rejected();
        }
        int travelSecond = travelEstimate.durationSecond();
        Instant arrivalAt = route.currentTime.plusSeconds(travelSecond);
        if (candidate.shipment().getRequestedFrom() != null
                && arrivalAt.isBefore(candidate.shipment().getRequestedFrom())) {
            arrivalAt = candidate.shipment().getRequestedFrom();
        }
        if (candidate.shipment().getRequestedTo() != null
                && arrivalAt.isAfter(candidate.shipment().getRequestedTo())) {
            return CandidateEstimate.rejected();
        }

        Instant completedAt = arrivalAt.plusSeconds(constraints.serviceTimePerStopMin() * 60L);
        long durationSecond = Duration.between(constraints.routeStartAt(), completedAt).getSeconds();
        if (durationSecond > Integer.MAX_VALUE) {
            return CandidateEstimate.rejected();
        }
        if (constraints.maxRouteDurationMin() != null
                && durationSecond > constraints.maxRouteDurationMin() * 60L) {
            return CandidateEstimate.rejected();
        }
        return new CandidateEstimate(
                true,
                distanceMeter,
                travelSecond,
                arrivalAt,
                completedAt,
                travelEstimate.source()
        );
    }

    protected int distanceMeter(GeoPoint from, GeoPoint to) {
        return distanceCalculator.distanceMeter(from, to);
    }

    private static final class MutableRoute {
        private final Instant startAt;
        private final List<PlannedRouteStop> stops = new ArrayList<>();
        private GeoPoint currentPoint;
        private Instant currentTime;
        private int totalDistanceMeter;
        private int totalWeightGram;
        private long totalCodAmount;

        private MutableRoute(RoutePlanningConstraints constraints) {
            this.startAt = constraints.routeStartAt();
            this.currentPoint = constraints.origin();
            this.currentTime = constraints.routeStartAt();
        }

        private void add(RoutePlanningCandidate candidate, CandidateEstimate estimate) {
            stops.add(new PlannedRouteStop(
                    candidate,
                    stops.size() + 1,
                    estimate.distanceMeter(),
                    estimate.travelSecond(),
                    estimate.arrivalAt(),
                    estimate.source()

            ));
            currentPoint = candidate.destination();
            currentTime = estimate.completedAt();
            totalDistanceMeter += estimate.distanceMeter();
            totalWeightGram += candidate.shipment().getTotalWeightGram();
            totalCodAmount += candidate.shipment().getCodAmount();
        }

        private PlannedRoute toPlannedRoute() {
            long duration = Duration.between(startAt, currentTime).getSeconds();
            return new PlannedRoute(
                    stops,
                    totalDistanceMeter,
                    Math.toIntExact(duration),
                    totalWeightGram,
                    totalCodAmount
            );
        }
    }
    private record CandidateEstimate(
            boolean accepted,
            int distanceMeter,
            int travelSecond,
            Instant arrivalAt,
            Instant completedAt,
            TravelEstimateSource source
    ) {
        private static CandidateEstimate rejected() {
            return new CandidateEstimate(false, 0, 0, null, null, null);
        }
    }
}
