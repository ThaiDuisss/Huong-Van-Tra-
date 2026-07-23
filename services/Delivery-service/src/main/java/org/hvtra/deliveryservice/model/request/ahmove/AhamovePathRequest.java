package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AhamovePathRequest(
        BigDecimal lat,
        BigDecimal lng,
        String address,
        @JsonProperty("short_address") String shortAddress,
        String name,
        String mobile,
        String remarks,
        Long cod,
        @JsonProperty("item_value") Long itemValue,
        @JsonProperty("tracking_number") String trackingNumber
) {
}
