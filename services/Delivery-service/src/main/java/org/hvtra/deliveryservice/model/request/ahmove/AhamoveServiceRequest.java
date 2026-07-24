package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AhamoveServiceRequest(
        @JsonProperty("_id") String id,
        List<AhamoveSpecialRequest> requests
) {
}
