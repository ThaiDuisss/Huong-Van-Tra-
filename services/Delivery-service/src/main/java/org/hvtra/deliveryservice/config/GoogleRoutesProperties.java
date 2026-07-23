package org.hvtra.deliveryservice.config;

import org.hvtra.deliveryservice.model.enums.GoogleRoutesRoutingPreference;
import org.hvtra.deliveryservice.model.enums.GoogleRoutesTravelMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delivery.internal-routing.google-routes")
public record GoogleRoutesProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        GoogleRoutesTravelMode travelMode,
        GoogleRoutesRoutingPreference routingPreference,
        boolean avoidTolls,
        boolean avoidHighways,
        boolean avoidFerries,
        int failureCooldownSeconds
) {
    public boolean configured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public GoogleRoutesTravelMode resolvedTravelMode() {
        return travelMode == null ? GoogleRoutesTravelMode.DRIVE : travelMode;
    }

    public GoogleRoutesRoutingPreference resolvedRoutingPreference() {
        return routingPreference == null
                ? GoogleRoutesRoutingPreference.TRAFFIC_AWARE
                : routingPreference;
    }

    public int resolvedFailureCooldownSeconds() {
        return failureCooldownSeconds <= 0 ? 30 : failureCooldownSeconds;
    }
}
