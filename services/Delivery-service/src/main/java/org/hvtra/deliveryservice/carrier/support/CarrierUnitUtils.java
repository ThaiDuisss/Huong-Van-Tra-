package org.hvtra.deliveryservice.carrier.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CarrierUnitUtils {

    private static final BigDecimal GRAMS_PER_KILOGRAM = BigDecimal.valueOf(1000);

    private CarrierUnitUtils() {
    }

    public static BigDecimal gramsToKilograms(long grams) {
        if (grams <= 0) {
            throw new IllegalArgumentException("Weight in grams must be positive");
        }
        return BigDecimal.valueOf(grams)
                .divide(GRAMS_PER_KILOGRAM, 3, RoundingMode.UP);
    }

    public static BigDecimal positiveCentimeters(Integer centimeters) {
        return centimeters == null || centimeters <= 0
                ? null
                : BigDecimal.valueOf(centimeters);
    }
}
