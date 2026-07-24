package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveCityResponse(
        @JsonProperty("_id") String id,
        String name,
        @JsonProperty("name_vi_vn") String nameViVn,
        @JsonProperty("country_id") String countryId
) {
}
