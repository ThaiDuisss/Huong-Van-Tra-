package org.hvtra.deliveryservice.model.request.ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnAvailableServicesRequest(
        @JsonProperty("shop_id") Integer shopId,
        @JsonProperty("from_district") Integer fromDistrict,
        @JsonProperty("to_district") Integer toDistrict
) {
}
