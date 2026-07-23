package org.hvtra.deliveryservice.carrier.support;

import java.util.Locale;

public final class CarrierTextUtils {

    private CarrierTextUtils() {
    }

    public static String required(String value, String field, String provider) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for " + provider);
        }
        return value.trim();
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String normalizePhone(String value, String field, String provider) {
        String phone = required(value, field, provider).replaceAll("[^0-9+]", "");
        if (phone.startsWith("+84")) {
            phone = "84" + phone.substring(3);
        }
        if (!phone.matches("(?:0|84)[0-9]{8,10}")) {
            throw new IllegalArgumentException(field + " is not a valid Vietnamese phone number");
        }
        return phone;
    }

    public static String normalizeStatus(String value) {
        return value == null ? "" : value.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
    }
}
