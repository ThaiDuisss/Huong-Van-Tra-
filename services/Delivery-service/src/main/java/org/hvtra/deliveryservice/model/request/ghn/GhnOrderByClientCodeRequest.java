package org.hvtra.deliveryservice.model.request.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnOrderByClientCodeRequest(@JsonProperty("client_order_code") String clientOrderCode) {
}
