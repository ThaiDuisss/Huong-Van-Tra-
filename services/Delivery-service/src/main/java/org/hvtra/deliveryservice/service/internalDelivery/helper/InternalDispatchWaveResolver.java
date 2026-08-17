package org.hvtra.deliveryservice.service.internalDelivery.helper;

import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.enums.DispatchWave;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class InternalDispatchWaveResolver {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime DEFAULT_MORNING_CUTOFF = LocalTime.of(11, 0);
    private static final LocalTime DEFAULT_AFTERNOON_CUTOFF = LocalTime.of(16, 0);
    private static final LocalTime DEFAULT_EVENING_CUTOFF = LocalTime.of(20, 0);

    public DispatchWave resolve(
            DispatchWave requestedWave,
            Instant plannedStartAt,
            TenantDeliveryConfig config
    ) {
        if (requestedWave != null) {
            return requestedWave;
        }
        LocalTime localTime = plannedStartAt.atZone(VIETNAM_ZONE).toLocalTime();
        LocalTime morning = config.getMorningCutoffTime() == null
                ? DEFAULT_MORNING_CUTOFF
                : config.getMorningCutoffTime();
        LocalTime afternoon = config.getAfternoonCutoffTime() == null
                ? DEFAULT_AFTERNOON_CUTOFF
                : config.getAfternoonCutoffTime();
        LocalTime evening = config.getEveningCutoffTime() == null
                ? DEFAULT_EVENING_CUTOFF
                : config.getEveningCutoffTime();
        if (!localTime.isAfter(morning)) {
            return DispatchWave.MORNING;
        }
        if (!localTime.isAfter(afternoon)) {
            return DispatchWave.AFTERNOON;
        }
        if (!localTime.isAfter(evening)) {
            return DispatchWave.EVENING;
        }
        return DispatchWave.EXPRESS;
    }
}
