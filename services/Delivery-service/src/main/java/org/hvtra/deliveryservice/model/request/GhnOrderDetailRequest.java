package org.hvtra.deliveryservice.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnOrderDetailRequest(@JsonProperty("order_code") String orderCode) {
}
