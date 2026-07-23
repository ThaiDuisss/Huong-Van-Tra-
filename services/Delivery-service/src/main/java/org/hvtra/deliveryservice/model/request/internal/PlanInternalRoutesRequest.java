package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hvtra.deliveryservice.model.enums.DispatchWave;

import java.time.Instant;
import java.util.UUID;

public record PlanInternalRoutesRequest(
        @NotNull UUID tenantId,
        DispatchWave dispatchWave,
        Instant plannedStartAt,
        @Min(1) @Max(100) Integer maxRoutes,
        @Min(1) @Max(1000) Integer candidateLimit
) {
    public int resolvedMaxRoutes() {
        return maxRoutes == null ? 20 : maxRoutes;
    }

    public int resolvedCandidateLimit(int maxOrdersPerRoute) {
        int defaultLimit = Math.min(Math.multiplyExact(resolvedMaxRoutes(), maxOrdersPerRoute), 1_000);
        return candidateLimit == null ? defaultLimit : candidateLimit;
    }
}
