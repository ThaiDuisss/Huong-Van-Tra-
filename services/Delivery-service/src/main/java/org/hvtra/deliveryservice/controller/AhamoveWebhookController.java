package org.hvtra.deliveryservice.controller;

import com.fasterxml.jackson.databind.JsonNode;

import org.hvtra.deliveryservice.carrier.model.CarrierWebhookAck;
import org.hvtra.deliveryservice.service.ahmove.AhamoveWebhookService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/public/webhooks")
public class AhamoveWebhookController {

    private final AhamoveWebhookService webhookService;

    public AhamoveWebhookController(AhamoveWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/ahamove")
    public CarrierWebhookAck receive(
            @RequestBody JsonNode payload,
            @RequestHeader HttpHeaders headers
    ) {
        return webhookService.process(payload, headers);
    }
}
