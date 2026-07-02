package org.hvtra.paymentservice.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.config.SepayProperties;
import org.hvtra.paymentservice.dto.response.SepayWebhookResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SepayWebhookService {

    private final SepayProperties properties;

   public SepayWebhookResponse handle (String rawBody, String signature, String timestamp, String authorization) {
       return null;
   }
}