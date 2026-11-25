package org.hvtra.catalog.outbox.polling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.UUID;

@ConfigurationProperties("catalog.outbox.item-sync")
public record CatalogItemSyncProperties(
        @DefaultValue("CDC") Mode mode,
        @DefaultValue("false") boolean cdcFenced,
        UUID activationId,
        @DefaultValue("1000") long intervalMs,
        @DefaultValue("25") int batchSize,
        @DefaultValue("12000") int ackTimeoutMs,
        @DefaultValue("60000") long leaseMs,
        @DefaultValue("1000") long retryInitialMs,
        @DefaultValue("120000") long retryMaxMs
) {
    public enum Mode { CDC, SHADOW, POLLING }

    public CatalogItemSyncProperties {
        if (mode == Mode.POLLING && (!cdcFenced || activationId == null)) {
            throw new IllegalArgumentException("ITEM_SYNC polling requires a fenced CDC and an approved activation ID");
        }
        if (intervalMs < 1 || batchSize < 1 || batchSize > 100 || ackTimeoutMs < 100
                || leaseMs <= 3L * ackTimeoutMs || leaseMs > 3600000
                || retryInitialMs < 1 || retryMaxMs < retryInitialMs || retryMaxMs > 3600000) {
            throw new IllegalArgumentException("Invalid ITEM_SYNC polling timing or batch limit");
        }
    }

    long retryDelayMs(int attempts) {
        return Math.min(retryMaxMs, retryInitialMs << Math.clamp(attempts - 1, 0, 22));
    }
}
