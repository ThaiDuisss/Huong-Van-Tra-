package org.hvtra.deliveryservice.support;

import org.hvtra.deliveryservice.carrier.support.CarrierProviderUtils;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarrierProviderUtilsTest {

    @Test
    void parsesProviderCaseInsensitively() {
        assertThat(CarrierProviderUtils.parse("ghn")).isEqualTo(DeliveryProvider.GHN);
        assertThat(CarrierProviderUtils.parse("AHaMove")).isEqualTo(DeliveryProvider.AHAMOVE);
    }

    @Test
    void rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> CarrierProviderUtils.parse("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported delivery provider");
    }
}
