package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveServiceResponse(
        @JsonProperty("_id") String id,
        @JsonProperty("city_id") String cityId,
        @JsonProperty("delivery_type") String deliveryType,
        @JsonProperty("description_vi_vn") String descriptionViVn,
        @JsonProperty("description_en_us") String descriptionEnUs,
        String currency,
        Long cod,
        List<AhamoveSpecialRequestResponse> requests
) {
}
