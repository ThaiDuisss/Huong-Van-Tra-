package org.hvtra.deliveryservice.service.internalDelivery.planning;

import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningCandidate;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RouteTravelEstimator;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class EarliestDeadlineRoutePlanningAlgorithm extends AbstractGreedyRoutePlanningAlgorithm {
    public EarliestDeadlineRoutePlanningAlgorithm(HaversineDistanceCalculator distanceCalculator, RouteTravelEstimator travelEstimator) {
        super(distanceCalculator, travelEstimator);
    }

    @Override
    protected Comparator<RoutePlanningCandidate> candidateOrder(GeoPoint currentPoint) {
        return Comparator
                .comparing((RoutePlanningCandidate candidate) -> candidate.shipment().getRequestedTo(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(candidate -> distanceMeter(currentPoint, candidate.destination()))
                .thenComparing(candidate -> candidate.shipment().getShipmentCode());
    }

    @Override
    public RoutePlanningAlgorithmType type() {
        return RoutePlanningAlgorithmType.EARLIEST_DEADLINE;
    }
}
