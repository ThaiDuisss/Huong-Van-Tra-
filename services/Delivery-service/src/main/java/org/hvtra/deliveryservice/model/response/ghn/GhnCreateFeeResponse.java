package org.hvtra.deliveryservice.model.response.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnCreateFeeResponse(
        Long coupon,
        Long insurance,
        @JsonProperty("main_service") Long mainService,
        Long r2s,
        @JsonProperty("return") Long returnFee,
        @JsonProperty("station_do") Long stationDo,
        @JsonProperty("station_pu") Long stationPu,
        @JsonProperty("cod_fee") Long codFee
) {
}
