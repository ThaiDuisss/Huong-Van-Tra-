package org.hvtra.deliveryservice.model.request.ahmove;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AhamoveSpecialRequest(
        @JsonProperty("_id") String id,
        Integer num,
        @JsonProperty("tier_code") String tierCode,
        List<AhamoveSpecialRequestOption> options,
        @JsonProperty("path_index") Integer pathIndex
) {
}
