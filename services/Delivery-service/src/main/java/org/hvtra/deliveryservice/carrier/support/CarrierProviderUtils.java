package org.hvtra.deliveryservice.carrier.support;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;

import java.util.Locale;

public final class CarrierProviderUtils {

    private CarrierProviderUtils() {
    }

    public static DeliveryProvider parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Delivery provider is required");
        }
        try {
            return DeliveryProvider.valueOf(
                    value.trim().replace('-', '_').toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported delivery provider: " + value, exception);
        }
    }
}
