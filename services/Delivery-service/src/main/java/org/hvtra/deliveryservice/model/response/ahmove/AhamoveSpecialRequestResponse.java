package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamoveSpecialRequestResponse(
        @JsonProperty("_id") String id,
        @JsonProperty("group_id") String groupId,
        String name,
        @JsonProperty("description_vi_vn") String descriptionViVn,
        @JsonProperty("description_en_us") String descriptionEnUs,
        Boolean enable,
        Long price,
        @JsonProperty("max_input") Integer maxInput,
        @JsonProperty("tier_list") JsonNode tierList,
        @JsonProperty("request_type") String requestType,
        String type,
        JsonNode options
) {
}
