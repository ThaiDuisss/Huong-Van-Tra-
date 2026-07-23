package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AhamoveItemRequest(
        @JsonProperty("_id") String id,
        Integer num,
        String name,
        Long price
) {
}
