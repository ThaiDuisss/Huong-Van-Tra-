package org.hvtra.deliveryservice.service.internalDelivery.helper;

import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

//công thức tính khoangr cách ngắn nhất (Đường chim bay) giữa 2 địa điểm
@Component
public class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_METER = 6_371_000;

    public int distanceMeter(GeoPoint from, GeoPoint to) {
        double latitude1 = Math.toRadians(from.latitude().doubleValue());
        double latitude2 = Math.toRadians(to.latitude().doubleValue());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());
        double sinLatitude = Math.sin(latitudeDelta / 2);
        double sinLongitude = Math.sin(longitudeDelta / 2);
        double a = sinLatitude * sinLatitude
                + Math.cos(latitude1) * Math.cos(latitude2) * sinLongitude * sinLongitude;
        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        long rounded = Math.round(EARTH_RADIUS_METER * centralAngle);
        return rounded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
    }

    public BigDecimal distanceKm(GeoPoint from, GeoPoint to) {
        return BigDecimal.valueOf(distanceMeter(from, to))
                .divide(BigDecimal.valueOf(1_000), 3, RoundingMode.HALF_UP);
    }
}
