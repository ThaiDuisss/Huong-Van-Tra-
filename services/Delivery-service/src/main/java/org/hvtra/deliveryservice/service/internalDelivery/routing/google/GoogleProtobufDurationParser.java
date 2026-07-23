package org.hvtra.deliveryservice.service.internalDelivery.routing.google;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GoogleProtobufDurationParser {

    private GoogleProtobufDurationParser() {
    }

    public static int parseSecond(String value) {
        if (value == null || value.length() < 2 || !value.endsWith("s")) {
            throw new IllegalArgumentException("Invalid Google duration");
        }
        BigDecimal seconds = new BigDecimal(value.substring(0, value.length() - 1));
        if (seconds.signum() < 0) {
            throw new IllegalArgumentException("Google duration must not be negative");
        }
        return seconds.setScale(0, RoundingMode.CEILING).intValueExact();
    }
}
