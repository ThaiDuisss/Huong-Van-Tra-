package org.hvtra.deliveryservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delivery.providers.ahamove")
public record AhamoveProperties(
        boolean enabled,
        String baseUrl,
        String token,
        AhamoveWebhookAuthType webhookAuthType,
        String webhookSecret,
        String webhookUsername
) {
    public void validate() {
        if (!enabled) {
            throw new IllegalStateException("Ahamove integration is disabled");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Ahamove base URL is not configured");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Ahamove token is not configured");
        }
    }

    public void validateWebhook() {
        if (!enabled) {
            throw new IllegalStateException("Ahamove integration is disabled");
        }
        AhamoveWebhookAuthType authType = resolvedWebhookAuthType();
        if (authType != AhamoveWebhookAuthType.NONE
                && (webhookSecret == null || webhookSecret.isBlank())) {
            throw new IllegalStateException("Ahamove webhook secret is not configured");
        }
        if (authType == AhamoveWebhookAuthType.BASIC_AUTH
                && (webhookUsername == null || webhookUsername.isBlank())) {
            throw new IllegalStateException("Ahamove webhook username is not configured");
        }
    }

    public AhamoveWebhookAuthType resolvedWebhookAuthType() {
        return webhookAuthType == null ? AhamoveWebhookAuthType.API_KEY : webhookAuthType;
    }
}
