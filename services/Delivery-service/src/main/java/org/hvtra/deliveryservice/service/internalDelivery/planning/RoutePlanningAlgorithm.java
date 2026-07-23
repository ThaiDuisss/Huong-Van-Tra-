package org.hvtra.deliveryservice.service.internalDelivery.planning;

import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningCandidate;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningConstraints;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningOutcome;

import java.util.List;

public interface RoutePlanningAlgorithm {

    RoutePlanningAlgorithmType type();

    RoutePlanningOutcome plan(List<RoutePlanningCandidate> candidates, RoutePlanningConstraints constraints);
}
