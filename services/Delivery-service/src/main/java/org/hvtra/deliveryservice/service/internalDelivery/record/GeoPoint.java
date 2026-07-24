package org.hvtra.deliveryservice.service.internalDelivery.record;

import java.math.BigDecimal;
import java.util.Objects;

public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public GeoPoint {
        Objects.requireNonNull(latitude, "latitude is required");
        Objects.requireNonNull(longitude, "longitude is required");
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
