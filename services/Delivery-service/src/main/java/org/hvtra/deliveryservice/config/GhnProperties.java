package org.hvtra.deliveryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delivery.providers.ghn")
public record GhnProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Integer shopId,
        Integer clientId,
        String webhookSecret
) {
    public void validate() {
        if (!enabled) {
            throw new IllegalStateException("GHN integration is disabled");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GHN token is not configured");
        }
        if (shopId == null || shopId <= 0) {
            throw new IllegalStateException("GHN shop id is not configured");
        }
    }
}
