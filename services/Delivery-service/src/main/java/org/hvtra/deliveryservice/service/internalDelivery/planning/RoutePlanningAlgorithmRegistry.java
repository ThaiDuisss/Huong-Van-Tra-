package org.hvtra.deliveryservice.service.internalDelivery.planning;

import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutePlanningAlgorithmRegistry {
    private final Map<RoutePlanningAlgorithmType, RoutePlanningAlgorithm> algorithms;

    public RoutePlanningAlgorithmRegistry(List<RoutePlanningAlgorithm> algorithmList) {
        EnumMap<RoutePlanningAlgorithmType, RoutePlanningAlgorithm> registry =
                new EnumMap<>(RoutePlanningAlgorithmType.class);
        for (RoutePlanningAlgorithm algorithm : algorithmList) {
            RoutePlanningAlgorithm previous = registry.put(algorithm.type(), algorithm);
            if (previous != null) {
                throw new IllegalStateException("Duplicate route planning algorithm: " + algorithm.type());
            }
        }
        this.algorithms = Map.copyOf(registry);
    }

    public RoutePlanningAlgorithm require(RoutePlanningAlgorithmType type) {
        RoutePlanningAlgorithm algorithm = algorithms.get(type);
        if (algorithm == null) {
            throw new IllegalArgumentException("Unsupported route planning algorithm: " + type);
        }
        return algorithm;
    }

}
