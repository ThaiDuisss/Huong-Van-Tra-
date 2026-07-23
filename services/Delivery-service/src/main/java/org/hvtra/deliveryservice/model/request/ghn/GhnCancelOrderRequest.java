package org.hvtra.deliveryservice.model.request.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GhnCancelOrderRequest(@JsonProperty("order_codes") List<String> orderCodes) {
}
