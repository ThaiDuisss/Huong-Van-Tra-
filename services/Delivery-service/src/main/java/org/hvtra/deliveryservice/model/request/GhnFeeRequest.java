package org.hvtra.deliveryservice.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GhnFeeRequest(
        @JsonProperty("from_district_id") Integer fromDistrictId,
        @JsonProperty("from_ward_code") String fromWardCode,
        @JsonProperty("to_district_id") Integer toDistrictId,
        @JsonProperty("to_ward_code") String toWardCode,
        @JsonProperty("service_id") Integer serviceId,
        @JsonProperty("service_type_id") Integer serviceTypeId,
        Integer weight,
        Integer length,
        Integer width,
        Integer height,
        @JsonProperty("insurance_value") Integer insuranceValue,
        @JsonProperty("cod_value") Integer codValue,
        @JsonProperty("cod_failed_amount") Integer codFailedAmount,
        String coupon,
        List<GhnItemRequest> items
) {
}
