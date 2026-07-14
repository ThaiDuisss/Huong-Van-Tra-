package org.hvtra.deliveryservice.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnServiceResponse(
        @JsonProperty("service_id") Integer serviceId,
        @JsonProperty("short_name") String shortName,
        @JsonProperty("service_type_id") Integer serviceTypeId
) {
}
