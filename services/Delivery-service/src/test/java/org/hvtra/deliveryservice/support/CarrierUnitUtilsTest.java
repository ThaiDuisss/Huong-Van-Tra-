package org.hvtra.deliveryservice.support;

import org.hvtra.deliveryservice.carrier.support.CarrierUnitUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarrierUnitUtilsTest {

    @Test
    void convertsGramsToKilogramsWithoutUnderstatingWeight() {
        assertThat(CarrierUnitUtils.gramsToKilograms(1)).isEqualByComparingTo(new BigDecimal("0.001"));
        assertThat(CarrierUnitUtils.gramsToKilograms(1501)).isEqualByComparingTo(new BigDecimal("1.501"));
    }

    @Test
    void keepsPositiveDimensionsInCentimeters() {
        assertThat(CarrierUnitUtils.positiveCentimeters(60)).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(CarrierUnitUtils.positiveCentimeters(0)).isNull();
        assertThat(CarrierUnitUtils.positiveCentimeters(null)).isNull();
    }

    @Test
    void rejectsUnknownWeight() {
        assertThatThrownBy(() -> CarrierUnitUtils.gramsToKilograms(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
