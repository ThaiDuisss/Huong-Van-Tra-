package org.hvtra.deliveryservice.model.response.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnCreateOrderResponse(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("sort_code") String sortCode,
        @JsonProperty("trans_type") String transType,
        @JsonProperty("ward_encode") String wardEncode,
        @JsonProperty("district_encode") String districtEncode,
        @JsonProperty("expected_delivery_time") Instant expectedDeliveryTime,
        GhnCreateFeeResponse fee,
        @JsonProperty("total_fee") Long totalFee
) {
}
