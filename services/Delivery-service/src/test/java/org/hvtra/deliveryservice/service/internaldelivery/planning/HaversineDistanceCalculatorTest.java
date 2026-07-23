package org.hvtra.deliveryservice.service.internaldelivery.planning;

import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void calculatesDistanceBetweenHanoiCoordinates() {
        GeoPoint from = new GeoPoint(new BigDecimal("21.0278"), new BigDecimal("105.8342"));
        GeoPoint to = new GeoPoint(new BigDecimal("21.0285"), new BigDecimal("105.8410"));

        assertThat(calculator.distanceMeter(from, to)).isBetween(650, 800);
    }

    @Test
    void rejectsInvalidCoordinate() {
        assertThatThrownBy(() -> new GeoPoint(BigDecimal.valueOf(91), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
