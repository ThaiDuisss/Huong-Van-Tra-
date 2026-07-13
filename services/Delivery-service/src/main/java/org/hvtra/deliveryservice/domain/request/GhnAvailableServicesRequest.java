package org.hvtra.deliveryservice.domain.request;

public record GhnAvailableServicesRequest(
        @JsonProperty("shop_id")
        Integer shopId,

        @JsonProperty("from_district")
        Integer fromDistrict,

        @JsonProperty("to_district")
        Integer toDistrict
) {
}