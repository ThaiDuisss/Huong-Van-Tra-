package org.hvtra.deliveryservice.service.internaldelivery.routing.google;

import org.hvtra.deliveryservice.client.GoogleRoutesClient;
import org.hvtra.deliveryservice.config.GoogleRoutesProperties;
import org.hvtra.deliveryservice.model.enums.GoogleRoutesRoutingPreference;
import org.hvtra.deliveryservice.model.enums.GoogleRoutesTravelMode;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.routing.TravelEstimate;
import org.hvtra.deliveryservice.service.internalDelivery.routing.google.GoogleRoutesResponse;
import org.hvtra.deliveryservice.service.internalDelivery.routing.google.GoogleRoutesRoadTravelEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleRoutesRoadTravelEstimatorTest {

    @Test
    void mapsGoogleDistanceAndTrafficDuration() {
        GoogleRoutesClient client = mock(GoogleRoutesClient.class);
        when(client.computeRoutes(anyString(), anyString(), any()))
                .thenReturn(new GoogleRoutesResponse(List.of(
                        new GoogleRoutesResponse.Route(3_250, "612.4s", "500s")
                )));
        GoogleRoutesProperties properties = new GoogleRoutesProperties(
                true,
                "https://routes.googleapis.com",
                "test-key",
                GoogleRoutesTravelMode.DRIVE,
                GoogleRoutesRoutingPreference.TRAFFIC_AWARE,
                false,
                false,
                true,
                30
        );
        GoogleRoutesRoadTravelEstimator estimator = new GoogleRoutesRoadTravelEstimator(client, properties);

        Optional<TravelEstimate> result = estimator.estimate(
                point("21.0278", "105.8342"),
                point("21.0285", "105.8410"),
                Instant.now().plusSeconds(60)
        );

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().distanceMeter()).isEqualTo(3_250);
        assertThat(result.orElseThrow().durationSecond()).isEqualTo(613);
        assertThat(result.orElseThrow().source()).isEqualTo(TravelEstimateSource.GOOGLE_ROUTES);
    }

    private static GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
