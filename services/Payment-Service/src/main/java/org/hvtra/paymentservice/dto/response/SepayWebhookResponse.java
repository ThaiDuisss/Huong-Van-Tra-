package org.hvtra.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class SepayWebhookResponse {
    private boolean success;
}