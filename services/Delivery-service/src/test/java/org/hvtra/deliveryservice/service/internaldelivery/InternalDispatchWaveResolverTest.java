package org.hvtra.deliveryservice.service.internaldelivery;

import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.enums.DispatchWave;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalDispatchWaveResolver;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

class InternalDispatchWaveResolverTest {

    private final InternalDispatchWaveResolver resolver = new InternalDispatchWaveResolver();

    @Test
    void resolvesWaveFromTenantCutoffsInVietnamTime() {
        TenantDeliveryConfig config = TenantDeliveryConfig.create(UUID.randomUUID());
        config.configureCutoffTimes(
                LocalTime.of(11, 0),
                LocalTime.of(16, 0),
                LocalTime.of(20, 0)
        );

        assertThat(resolver.resolve(null, Instant.parse("2026-07-20T03:00:00Z"), config))
                .isEqualTo(DispatchWave.MORNING);
        assertThat(resolver.resolve(null, Instant.parse("2026-07-20T07:00:00Z"), config))
                .isEqualTo(DispatchWave.AFTERNOON);
        assertThat(resolver.resolve(null, Instant.parse("2026-07-20T11:00:00Z"), config))
                .isEqualTo(DispatchWave.EVENING);
        assertThat(resolver.resolve(null, Instant.parse("2026-07-20T14:00:00Z"), config))
                .isEqualTo(DispatchWave.EXPRESS);
    }

    @Test
    void keepsExplicitWave() {
        TenantDeliveryConfig config = TenantDeliveryConfig.create(UUID.randomUUID());

        assertThat(resolver.resolve(DispatchWave.EXPRESS, Instant.EPOCH, config))
                .isEqualTo(DispatchWave.EXPRESS);
    }
}
