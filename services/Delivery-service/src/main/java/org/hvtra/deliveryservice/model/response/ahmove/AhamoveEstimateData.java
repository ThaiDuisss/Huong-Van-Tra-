package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveEstimateData(
        Double distance,
        Long duration,
        @JsonProperty("distance_fee") Long distanceFee,
        @JsonProperty("request_fee") Long requestFee,
        @JsonProperty("stop_fee") Long stopFee,
        @JsonProperty("vat_fee") Long vatFee,
        Long discount,
        @JsonProperty("total_fee") Long totalFee,
        @JsonProperty("total_price") Long totalPrice
) {
    public Long resolvedTotalFee() {
        return totalFee != null ? totalFee : totalPrice;
    }
}
