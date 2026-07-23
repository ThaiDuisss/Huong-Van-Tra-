package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveCreateOrderResponse(
        @JsonProperty("order_id") String orderId,
        String status,
        @JsonProperty("shared_link") String sharedLink,
        AhamoveOrderDetailResponse order
) {
}
