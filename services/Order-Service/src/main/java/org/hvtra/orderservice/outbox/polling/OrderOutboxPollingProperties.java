package org.hvtra.orderservice.outbox.polling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("order.outbox.publisher")
public record OrderOutboxPollingProperties(
        @DefaultValue("CDC") Mode mode,
        @DefaultValue("false") boolean cdcFenced,
        @DefaultValue("1000") long intervalMs,
        @DefaultValue("25") int batchSize,
        @DefaultValue("12000") int ackTimeoutMs,
        @DefaultValue("30000") long leaseMs,
        @DefaultValue("1000") long retryInitialMs,
        @DefaultValue("120000") long retryMaxMs
) {
    public enum Mode { CDC, POLLING }

    public OrderOutboxPollingProperties {
        if (mode == Mode.POLLING && !cdcFenced) {
            throw new IllegalArgumentException("Polling requires operator-confirmed CDC exclusion");
        }
        if (intervalMs < 1 || batchSize < 1 || batchSize > 100
                || ackTimeoutMs < 100 || leaseMs <= 2L * ackTimeoutMs || leaseMs > 3600000
                || retryInitialMs < 1 || retryMaxMs < retryInitialMs || retryMaxMs > 3600000) {
            throw new IllegalArgumentException("Invalid Order Outbox polling timing or batch limit");
        }
    }

    long retryDelayMs(int attempts) {
        int exponent = Math.clamp(attempts - 1, 0, 22);
        return Math.min(retryMaxMs, retryInitialMs << exponent);
    }
}
