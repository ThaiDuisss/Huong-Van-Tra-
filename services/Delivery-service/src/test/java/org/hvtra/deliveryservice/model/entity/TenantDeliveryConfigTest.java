package org.hvtra.deliveryservice.model.entity;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantDeliveryConfigTest {

    @Test
    void appliesLimitsAndCalculatesInternalFee() {
        TenantDeliveryConfig config = TenantDeliveryConfig.create(UUID.randomUUID());
        config.configureInternalDelivery(
                true,
                new BigDecimal("10.00"),
                20_000,
                2_000_000L,
                240,
                8,
                15_000,
                5_000,
                DeliveryProvider.GHN
        );
        config.configurePlanning(
                RoutePlanningAlgorithmType.NEAREST_NEIGHBOR,
                BigDecimal.valueOf(25),
                TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK,
                5
        );

        assertThat(config.supportsInternalDelivery(new BigDecimal("8.00"), 10_000, 1_000_000)).isTrue();
        assertThat(config.supportsInternalDelivery(new BigDecimal("11.00"), 10_000, 1_000_000)).isFalse();
        assertThat(config.calculateInternalShippingFee(new BigDecimal("2.50"))).isEqualTo(27_500);
        assertThat(config.getTravelEstimationMode())
                .isEqualTo(TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK);
    }

    @Test
    void rejectsInternalFallbackProvider() {
        TenantDeliveryConfig config = TenantDeliveryConfig.create(UUID.randomUUID());

        assertThatThrownBy(() -> config.configureInternalDelivery(
                true,
                null,
                null,
                null,
                null,
                10,
                0,
                0,
                DeliveryProvider.STORE_COURIER
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCutoffsInWrongOrder() {
        TenantDeliveryConfig config = TenantDeliveryConfig.create(UUID.randomUUID());

        assertThatThrownBy(() -> config.configureCutoffTimes(
                LocalTime.of(14, 0),
                LocalTime.of(11, 0),
                LocalTime.of(20, 0)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
