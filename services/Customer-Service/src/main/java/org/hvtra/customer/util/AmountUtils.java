package org.hvtra.customer.util;

import java.math.BigDecimal;

public final class AmountUtils {
    private AmountUtils() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    public static BigDecimal nonNegative(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }
}
