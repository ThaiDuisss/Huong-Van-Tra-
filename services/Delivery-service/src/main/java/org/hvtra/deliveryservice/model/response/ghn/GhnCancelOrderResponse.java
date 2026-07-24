package org.hvtra.deliveryservice.model.response.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnCancelOrderResponse(
        @JsonProperty("order_code") String orderCode,
        boolean result,
        String message
) {
}
