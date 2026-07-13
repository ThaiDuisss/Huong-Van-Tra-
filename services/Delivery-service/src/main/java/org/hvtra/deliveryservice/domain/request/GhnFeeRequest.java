package org.hvtra.deliveryservice.domain.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GhnFeeRequest(
        @JsonProperty("from_district_id")
        Integer fromDistrictId,

        @JsonProperty("from_ward_code")
        String fromWardCode,

        @JsonProperty("to_district_id")
        Integer toDistrictId,

        @JsonProperty("to_ward_code")
        String toWardCode,

        @JsonProperty("service_id")
        Integer serviceId,

        @JsonProperty("service_type_id")
        Integer serviceTypeId,

        Integer weight,
        Integer length,
        Integer width,
        Integer height,

        @JsonProperty("insurance_value")
        Integer insuranceValue,

        List<GhnItemRequest> items
) {
}