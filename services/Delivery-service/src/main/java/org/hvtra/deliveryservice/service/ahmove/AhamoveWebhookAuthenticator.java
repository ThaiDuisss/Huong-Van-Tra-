package org.hvtra.deliveryservice.service.ahmove;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.carrier.support.CarrierWebhookSupport;
import org.hvtra.deliveryservice.config.AhamoveProperties;
import org.hvtra.deliveryservice.config.AhamoveWebhookAuthType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class AhamoveWebhookAuthenticator {

    private final AhamoveProperties properties;
    private final CarrierWebhookSupport webhookSupport;


    public void validate(HttpHeaders headers, JsonNode payload) {
        properties.validateWebhook();
        AhamoveWebhookAuthType authType = properties.resolvedWebhookAuthType();
        if (authType == AhamoveWebhookAuthType.NONE) {
            return;
        }
        String secret = properties.webhookSecret();
        boolean valid = switch (authType) {
            case API_KEY -> validateApiKey(headers, payload, secret);
            case BEARER_TOKEN -> validateBearer(headers, secret);
            case BASIC_AUTH -> validateBasic(headers, secret);
            case NONE -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid Ahamove webhook authentication");
        }
    }

    private boolean validateApiKey(HttpHeaders headers, JsonNode payload, String secret) {
        String supplied = headers.getFirst("apikey");
        if (supplied == null || supplied.isBlank()) {
            supplied = webhookSupport.text(payload, "api_key", "apikey");
        }
        return webhookSupport.constantTimeEquals(secret, supplied);
    }

    private boolean validateBearer(HttpHeaders headers, String secret) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String supplied = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim()
                : "";
        return webhookSupport.constantTimeEquals(secret, supplied);
    }

    private boolean validateBasic(HttpHeaders headers, String secret) {
        String username = properties.webhookUsername() == null ? "" : properties.webhookUsername();
        String expected = Base64.getEncoder().encodeToString(
                (username + ":" + secret).getBytes(StandardCharsets.UTF_8)
        );
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        String supplied = authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)
                ? authorization.substring(6).trim()
                : "";
        return webhookSupport.constantTimeEquals(expected, supplied);
    }
}
