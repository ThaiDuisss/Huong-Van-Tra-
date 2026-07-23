package org.hvtra.deliveryservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.deliveryservice.model.response.ghn.GhnWebhookAck;
import org.hvtra.deliveryservice.service.ghn.GhnWebhookService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/public/webhooks")
public class GhnWebhookController {

    private final GhnWebhookService webhookService;

    public GhnWebhookController(GhnWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/ghn")
    public GhnWebhookAck receive(
            @RequestBody JsonNode payload,
            @RequestHeader HttpHeaders headers,
            @RequestHeader(value = "X-GHN-Webhook-Secret", required = false) String headerSecret,
            @RequestParam(value = "secret", required = false) String querySecret
    ) {
        String secret = headerSecret == null || headerSecret.isBlank() ? querySecret : headerSecret;
        return webhookService.process(payload, headers, secret);
    }
}
