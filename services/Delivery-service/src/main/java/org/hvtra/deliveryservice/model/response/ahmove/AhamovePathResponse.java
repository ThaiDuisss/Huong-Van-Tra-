package org.hvtra.deliveryservice.model.response.ahmove;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AhamovePathResponse(
        String address,
        String mobile,
        String name,
        Long cod,
        @JsonProperty("item_value") Long itemValue,
        @JsonProperty("tracking_number") String trackingNumber,
        String status,
        @JsonProperty("fail_comment") String failComment,
        @JsonProperty("complete_time") Double completeTime,
        @JsonProperty("fail_time") Double failTime,
        @JsonProperty("return_time") Double returnTime,
        @JsonProperty("pod_info") String podInfo,
        @JsonProperty("image_url") String imageUrl
) {
}
